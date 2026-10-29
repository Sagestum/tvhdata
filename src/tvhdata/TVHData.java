/*
 * Copyright (c) 2026 Christian Echterling
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the project (TVHData) nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package tvhdata;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import devplugin.AbstractTvDataService;
import devplugin.Channel;
import devplugin.ChannelGroup;
import devplugin.ChannelGroupImpl;
import devplugin.Date;
import devplugin.PluginInfo;
import devplugin.Program;
import devplugin.ProgramFieldType;
import devplugin.ProgressMonitor;
import devplugin.Version;
import tvdataservice.MutableChannelDayProgram;
import tvdataservice.MutableProgram;
import tvdataservice.SettingsPanel;
import tvdataservice.TvDataUpdateManager;
import util.exc.TvBrowserException;

/**
 * TV-Browser data service, which loads the channels and the EPG from a tvheadend server
 */
public class TVHData extends AbstractTvDataService {

    private static final Logger logger = Logger.getLogger(TVHData.class.getName());

    private static final util.i18n.Localizer mLocalizer = util.i18n.Localizer.getLocalizerFor(TVHData.class);

    private static final Version VERSION = new Version(0, 10, 0);

    static final String KEY_URL = "tvh.url";
    static final String KEY_USER = "tvh.user";
    static final String KEY_PASSWORD = "tvh.password";
    static final String KEY_TAG = "channel.tag";
    static final String KEY_TAG_NAME = "channel.tag.name";
    static final String KEY_LOGOS = "channel.logos";

    /** country of all channels; part of the channel ID in TV-Browser, so it must never change */
    private static final String COUNTRY = "de";

    /** maximum size of the channel logos */
    private static final int ICON_WIDTH = 120;
    private static final int ICON_HEIGHT = 30;

    private final ChannelGroup group = new ChannelGroupImpl("tvheadend", "tvheadend", "Channels from tvheadend", "tvheadend");

    private final List<Channel> channels = new ArrayList<>();

    private Properties settings = new Properties();

    private File workingDirectory;

    public static Version getVersion() {
        return VERSION;
    }

    public static String getTranslation(String key, String defaultValue) {
        return mLocalizer.msg(key, defaultValue);
    }

    @Override
    public PluginInfo getInfo() {
        return new PluginInfo(TVHData.class, "TVHData",
                getTranslation("desc", "Loads the channels and the EPG from a tvheadend server into TV-Browser."),
                "Christian Echterling");
    }

    TvhClient createClient() {
        return new TvhClient(settings.getProperty(KEY_URL, ""), settings.getProperty(KEY_USER, ""), settings.getProperty(KEY_PASSWORD, ""));
    }

    // ####################### settings ###########################

    @Override
    public void setWorkingDirectory(File dir) {
        workingDirectory = dir;
        for (Channel channel : channels) {
            Icon icon = loadIcon(channel.getId());
            if (icon != null) {
                channel.setDefaultIcon(icon);
            }
        }
    }

    @Override
    public void loadSettings(Properties p) {
        settings = p != null ? p : new Properties();
        if (settings.getProperty(KEY_URL) == null) {
            settings.setProperty(KEY_URL, "http://localhost:9981");
        }
        channels.clear();
        int count = Integer.parseInt(settings.getProperty("channel.count", "0"));
        for (int i = 0; i < count; i++) {
            String prefix = "channel." + i + ".";
            String id = settings.getProperty(prefix + "id");
            if (id == null) {
                continue;
            }
            String name = settings.getProperty(prefix + "name", id);
            String number = settings.getProperty(prefix + "number", "");
            int category = Integer.parseInt(settings.getProperty(prefix + "category", Integer.toString(Channel.CATEGORY_TV)));
            channels.add(createChannel(id, name, number, category, loadIcon(id)));
        }
    }

    @Override
    public Properties storeSettings() {
        // remove the old channel list, it can be longer than the current one
        settings.stringPropertyNames().stream().filter(key -> key.startsWith("channel.") && Character.isDigit(key.charAt(8)))
                .forEach(settings::remove);
        settings.setProperty("channel.count", Integer.toString(channels.size()));
        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            String prefix = "channel." + i + ".";
            settings.setProperty(prefix + "id", channel.getId());
            settings.setProperty(prefix + "name", channel.getDefaultName());
            settings.setProperty(prefix + "number", channel.getSortNumber() == null ? "" : channel.getSortNumber());
            settings.setProperty(prefix + "category", Integer.toString(channel.getCategories()));
        }
        return settings;
    }

    Properties getSettings() {
        return settings;
    }

    @Override
    public boolean hasSettingsPanel() {
        return true;
    }

    @Override
    public SettingsPanel getSettingsPanel() {
        return new TVHDataSettingsPanel(this);
    }

    // ####################### channels ###########################

    @Override
    public ChannelGroup[] getAvailableGroups() {
        return new ChannelGroup[] { group };
    }

    @Override
    public ChannelGroup[] checkForAvailableChannelGroups(ProgressMonitor monitor) throws TvBrowserException {
        return getAvailableGroups();
    }

    @Override
    public boolean supportsDynamicChannelList() {
        return true;
    }

    @Override
    public boolean supportsDynamicChannelGroups() {
        return false;
    }

    @Override
    public Channel[] getAvailableChannels(ChannelGroup g) {
        return channels.toArray(new Channel[0]);
    }

    @Override
    public Channel[] checkForAvailableChannels(ChannelGroup g, ProgressMonitor monitor) throws TvBrowserException {
        TvhClient client = createClient();
        List<Object> entries;
        Map<String, String> tagNames = new HashMap<>();
        try {
            monitor.setMessage(getTranslation("loading_channels", "Loading channels from tvheadend"));
            for (Object o : client.getEntries("/api/channeltag/grid", Map.of("limit", 100000))) {
                Map<String, Object> tag = Json.asMap(o);
                tagNames.put(Json.getString(tag, "uuid"), Json.getString(tag, "name"));
            }
            entries = client.getEntries("/api/channel/grid", Map.of("limit", 100000));
        } catch (Exception e) {
            throw new TvBrowserException(TVHData.class, "error_channels", "Couldn't load the channels from tvheadend:\n{0}", e.getLocalizedMessage(), e);
        }

        String tagFilter = settings.getProperty(KEY_TAG, "");
        List<Map<String, Object>> tvhChannels = new ArrayList<>();
        for (Object o : entries) {
            Map<String, Object> channel = Json.asMap(o);
            List<Object> tags = Json.asList(channel.get("tags"));
            if (channel.get("enabled") == Boolean.FALSE || (!tagFilter.isEmpty() && !tags.contains(tagFilter))) {
                continue;
            }
            tvhChannels.add(channel);
        }
        // channels without number at the end
        tvhChannels.sort(Comparator.comparingLong((Map<String, Object> c) -> Json.getLong(c, "number") > 0 ? Json.getLong(c, "number") : Long.MAX_VALUE)
                .thenComparing(c -> Json.getString(c, "name"), String.CASE_INSENSITIVE_ORDER));

        boolean logos = Boolean.parseBoolean(settings.getProperty(KEY_LOGOS, "true"));
        monitor.setMaximum(tvhChannels.size());
        List<Channel> result = new ArrayList<>();
        for (int i = 0; i < tvhChannels.size(); i++) {
            Map<String, Object> c = tvhChannels.get(i);
            monitor.setValue(i);
            String uuid = Json.getString(c, "uuid");
            String name = Json.getString(c, "name");
            long number = Json.getLong(c, "number");
            int category = Channel.CATEGORY_TV | Channel.CATEGORY_DIGITAL;
            for (Object tag : Json.asList(c.get("tags"))) {
                if (tagNames.getOrDefault(tag, "").toLowerCase().contains("radio")) {
                    category = Channel.CATEGORY_RADIO;
                }
            }
            Icon icon = null;
            String iconUrl = Json.getString(c, "icon_public_url");
            if (logos && !iconUrl.isEmpty()) {
                icon = downloadIcon(client, uuid, iconUrl);
            }
            result.add(createChannel(uuid, name, number > 0 ? Long.toString(number) : "", category, icon));
        }
        channels.clear();
        channels.addAll(result);
        return getAvailableChannels(g);
    }

    private Channel createChannel(String uuid, String name, String number, int category, Icon icon) {
        return new Channel(this, name, uuid, TimeZone.getDefault(), COUNTRY, "tvheadend", "", group, icon, category, name, null, number);
    }

    private File getIconFile(String uuid) {
        return workingDirectory == null ? null : new File(new File(workingDirectory, "icons"), uuid + ".png");
    }

    private Icon loadIcon(String uuid) {
        File file = getIconFile(uuid);
        return file != null && file.isFile() ? new ImageIcon(file.getAbsolutePath()) : null;
    }

    /**
     * Downloads the logo of a channel, scales it down and stores it in the working directory
     */
    private Icon downloadIcon(TvhClient client, String uuid, String iconUrl) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(client.get(iconUrl)));
            if (image == null) {
                return null;
            }
            double scale = Math.min(1, Math.min((double) ICON_WIDTH / image.getWidth(), (double) ICON_HEIGHT / image.getHeight()));
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            scaled.createGraphics().drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            File file = getIconFile(uuid);
            if (file != null) {
                file.getParentFile().mkdirs();
                ImageIO.write(scaled, "png", file);
            }
            return new ImageIcon(scaled);
        } catch (Exception e) {
            logger.fine("Couldn't load the logo " + iconUrl + ": " + e);
            return null;
        }
    }

    // ####################### EPG ###########################

    @Override
    public void updateTvData(TvDataUpdateManager database, Channel[] channelArr, Date startDate, int dateCount, ProgressMonitor monitor)
            throws TvBrowserException {
        TvhClient client = createClient();
        monitor.setMaximum(channelArr.length);
        int failed = 0;
        Exception lastError = null;
        for (int i = 0; i < channelArr.length; i++) {
            if (database.cancelDownload()) {
                return;
            }
            Channel channel = channelArr[i];
            monitor.setValue(i);
            monitor.setMessage(getTranslation("loading_epg", "Loading EPG from tvheadend: {0}", channel.getName()));
            try {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("channel", channel.getId());
                params.put("limit", 100000);
                params.put("sort", "start");
                params.put("dir", "ASC");
                List<Object> events = client.getEntries("/api/epg/events/grid", params);
                for (MutableChannelDayProgram dayProgram : createDayPrograms(channel, events, startDate, dateCount)) {
                    database.updateDayProgram(dayProgram);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Couldn't load the EPG of " + channel.getName(), e);
                failed++;
                lastError = e;
            }
        }
        monitor.setMessage("");
        if (failed > 0 && failed == channelArr.length) {
            throw new TvBrowserException(TVHData.class, "error_epg", "Couldn't load the EPG from tvheadend:\n{0}", lastError.getLocalizedMessage(), lastError);
        }
    }

    public static String getTranslation(String key, String defaultValue, Object arg) {
        return mLocalizer.msg(key, defaultValue, arg);
    }

    /**
     * Distributes the events of a channel to the days in the requested range
     */
    private List<MutableChannelDayProgram> createDayPrograms(Channel channel, List<Object> events, Date startDate, int dateCount) {
        Date endDate = startDate.addDays(dateCount);
        Map<Date, MutableChannelDayProgram> days = new LinkedHashMap<>();
        Date firstDay = null;
        int firstStart = 0;
        for (Object o : events) {
            Map<String, Object> event = Json.asMap(o);
            long start = Json.getLong(event, "start");
            long stop = Json.getLong(event, "stop");
            ZonedDateTime time = Instant.ofEpochSecond(start).atZone(channel.getTimeZone().toZoneId());
            Date date = new Date(time.getYear(), time.getMonthValue(), time.getDayOfMonth());
            if (date.compareTo(startDate) < 0 || date.compareTo(endDate) >= 0) {
                continue;
            }
            if (firstDay == null) {
                firstDay = date;
                firstStart = time.getHour() * 60 + time.getMinute();
            }
            MutableProgram program = new MutableProgram(channel, date, time.getHour(), time.getMinute(), false);
            fillProgram(program, event, (int) ((stop - start) / 60));
            days.computeIfAbsent(date, d -> new MutableChannelDayProgram(d, channel)).addProgram(program);
        }

        // tvheadend only knows the EPG from now on. Keep the programs of today, which have already been shown,
        // otherwise they would vanish from TV-Browser with every update
        if (firstDay != null && firstStart > 0) {
            MutableChannelDayProgram newDay = days.get(firstDay);
            MutableChannelDayProgram merged = new MutableChannelDayProgram(firstDay, channel);
            Iterator<Program> it = getPluginManager() == null ? null : getPluginManager().getChannelDayProgram(firstDay, channel);
            while (it != null && it.hasNext()) {
                Program old = it.next();
                int oldEnd = old.getStartTime() + Math.max(0, old.getLength());
                if (old.getStartTime() < firstStart && oldEnd <= firstStart) {
                    merged.addProgram(copyProgram(old, channel));
                }
            }
            if (merged.getProgramCount() > 0) {
                for (int i = 0; i < newDay.getProgramCount(); i++) {
                    merged.addProgram(newDay.getProgramAt(i));
                }
                days.put(firstDay, merged);
            }
        }
        return new ArrayList<>(days.values());
    }

    private static void fillProgram(MutableProgram program, Map<String, Object> event, int length) {
        program.setTitle(Json.getString(event, "title"));
        String description = Json.getString(event, "description");
        if (description.isEmpty()) {
            description = Json.getString(event, "summary");
        }
        if (!description.isEmpty()) {
            program.setDescription(description);
            program.setShortInfo(MutableProgram.generateShortInfoFromDescription(description));
        }
        String subtitle = Json.getString(event, "subtitle");
        if (!subtitle.isEmpty()) {
            program.setTextField(ProgramFieldType.EPISODE_TYPE, subtitle);
        }
        if (length > 0) {
            program.setLength(length);
        }

        int info = 0;
        List<Object> genres = Json.asList(event.get("genre"));
        if (!genres.isEmpty() && genres.get(0) instanceof Number) {
            int genre = ((Number) genres.get(0)).intValue();
            info |= Genres.getInfoBits(genre);
            String genreName = Genres.getName(genre);
            if (genreName != null) {
                program.setTextField(ProgramFieldType.GENRE_TYPE, genreName);
            }
        }
        if (Json.getBoolean(event, "hd")) {
            info |= Program.INFO_VISION_HD;
        }
        if (Json.getBoolean(event, "widescreen")) {
            info |= Program.INFO_VISION_16_TO_9;
        }
        if (Json.getBoolean(event, "subtitled")) {
            info |= Program.INFO_SUBTITLE_FOR_AURALLY_HANDICAPPED;
        }
        if (Json.getBoolean(event, "audiodesc")) {
            info |= Program.INFO_AUDIO_DESCRIPTION;
        }
        if (info != 0) {
            program.setInfo(info);
        }

        setIntIfPositive(program, ProgramFieldType.AGE_LIMIT_TYPE, Json.getInt(event, "ageRating"));
        setIntIfPositive(program, ProgramFieldType.PRODUCTION_YEAR_TYPE, Json.getInt(event, "copyrightYear"));
        setIntIfPositive(program, ProgramFieldType.EPISODE_NUMBER_TYPE, Json.getInt(event, "episodeNumber"));
        setIntIfPositive(program, ProgramFieldType.SEASON_NUMBER_TYPE, Json.getInt(event, "seasonNumber"));
        program.setProgramLoadingIsComplete();
    }

    private static void setIntIfPositive(MutableProgram program, ProgramFieldType type, int value) {
        if (value > 0) {
            program.setIntField(type, value);
        }
    }

    private static MutableProgram copyProgram(Program old, Channel channel) {
        MutableProgram program = new MutableProgram(channel, old.getDate(), old.getHours(), old.getMinutes(), false);
        Iterator<ProgramFieldType> it = old.getFieldIterator();
        while (it.hasNext()) {
            ProgramFieldType type = it.next();
            switch (type.getFormat()) {
            case ProgramFieldType.FORMAT_TEXT:
                program.setTextField(type, old.getTextField(type));
                break;
            case ProgramFieldType.FORMAT_INT:
                program.setIntField(type, old.getIntField(type));
                break;
            case ProgramFieldType.FORMAT_TIME:
                program.setTimeField(type, old.getTimeField(type));
                break;
            case ProgramFieldType.FORMAT_BINARY:
                program.setBinaryField(type, old.getBinaryField(type));
                break;
            default:
                break;
            }
        }
        program.setProgramLoadingIsComplete();
        return program;
    }
}
