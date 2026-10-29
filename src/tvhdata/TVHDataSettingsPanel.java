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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import tvdataservice.SettingsPanel;

/**
 * Settings of the connection to tvheadend and of the channel list
 */
public class TVHDataSettingsPanel extends SettingsPanel {

    private static final long serialVersionUID = 1L;

    private final transient TVHData service;

    private final JTextField url = new JTextField(25);
    private final JTextField user = new JTextField(25);
    private final JPasswordField password = new JPasswordField(25);
    private final JComboBox<Tag> tags = new JComboBox<>();
    private final JButton loadTags = new JButton(TVHData.getTranslation("load_tags", "Load tags"));
    private final JLabel status = new JLabel(" ");
    private final JCheckBox logos = new JCheckBox(TVHData.getTranslation("logos", "Load channel logos"));

    public TVHDataSettingsPanel(TVHData service) {
        this.service = service;
        Properties settings = service.getSettings();
        url.setText(settings.getProperty(TVHData.KEY_URL, ""));
        user.setText(settings.getProperty(TVHData.KEY_USER, ""));
        password.setText(settings.getProperty(TVHData.KEY_PASSWORD, ""));
        logos.setSelected(Boolean.parseBoolean(settings.getProperty(TVHData.KEY_LOGOS, "true")));
        Tag selected = new Tag(settings.getProperty(TVHData.KEY_TAG, ""), settings.getProperty(TVHData.KEY_TAG_NAME, ""));
        setTags(new ArrayList<>(), selected);
        loadTags.addActionListener(e -> loadTags());
        initGUI();
        if (!url.getText().trim().isEmpty()) {
            loadTags();
        }
    }

    private void initGUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addRow(gbc, 0, TVHData.getTranslation("url", "tvheadend URL"), url);
        addRow(gbc, 1, TVHData.getTranslation("user", "User"), user);
        addRow(gbc, 2, TVHData.getTranslation("password", "Password"), password);
        addRow(gbc, 3, TVHData.getTranslation("tag", "Only channels with tag"), tags);
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        add(loadTags, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        add(status, gbc);
        gbc.gridy = 5;
        add(logos, gbc);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 3;
        add(new JLabel(TVHData.getTranslation("refresh_hint",
                "<html>After changing these settings, refresh the channel list in the channel settings of TV-Browser.</html>")), gbc);

        // push everything to the top
        gbc.gridy = 7;
        gbc.weighty = 1;
        add(new JLabel(), gbc);
    }

    private void addRow(GridBagConstraints gbc, int row, String label, java.awt.Component component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(component, gbc);
    }

    private void loadTags() {
        TvhClient client = new TvhClient(url.getText(), user.getText(), new String(password.getPassword()));
        Tag selected = (Tag) tags.getSelectedItem();
        loadTags.setEnabled(false);
        status.setText(TVHData.getTranslation("loading_tags", "Loading tags…"));
        new SwingWorker<List<Tag>, Void>() {
            @Override
            protected List<Tag> doInBackground() throws Exception {
                List<Tag> result = new ArrayList<>();
                for (Object o : client.getEntries("/api/channeltag/grid", Map.of("limit", 100000))) {
                    Map<String, Object> tag = Json.asMap(o);
                    if (tag.get("enabled") != Boolean.FALSE) {
                        result.add(new Tag(Json.getString(tag, "uuid"), Json.getString(tag, "name")));
                    }
                }
                result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                return result;
            }

            @Override
            protected void done() {
                loadTags.setEnabled(true);
                try {
                    List<Tag> result = get();
                    setTags(result, selected);
                    status.setText(TVHData.getTranslation("connection_ok", "Connected to tvheadend, {0} tags found.", result.size()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    status.setText(TVHData.getTranslation("connection_failed", "Connection failed: {0}", cause.getLocalizedMessage()));
                }
            }
        }.execute();
    }

    private void setTags(List<Tag> list, Tag selected) {
        tags.removeAllItems();
        tags.addItem(new Tag("", TVHData.getTranslation("all_channels", "(all channels)")));
        Tag select = null;
        for (Tag tag : list) {
            tags.addItem(tag);
            if (tag.uuid.equals(selected.uuid)) {
                select = tag;
            }
        }
        // keep the configured tag, even if the server is not reachable
        if (select == null && !selected.uuid.isEmpty()) {
            select = selected;
            tags.addItem(selected);
        }
        tags.setSelectedIndex(select == null ? 0 : ((javax.swing.DefaultComboBoxModel<Tag>) tags.getModel()).getIndexOf(select));
    }

    @Override
    public void ok() {
        Properties settings = service.getSettings();
        settings.setProperty(TVHData.KEY_URL, url.getText().trim());
        settings.setProperty(TVHData.KEY_USER, user.getText().trim());
        settings.setProperty(TVHData.KEY_PASSWORD, new String(password.getPassword()));
        Tag tag = (Tag) tags.getSelectedItem();
        settings.setProperty(TVHData.KEY_TAG, tag == null ? "" : tag.uuid);
        settings.setProperty(TVHData.KEY_TAG_NAME, tag == null || tag.uuid.isEmpty() ? "" : tag.name);
        settings.setProperty(TVHData.KEY_LOGOS, Boolean.toString(logos.isSelected()));
    }

    private static class Tag {
        private final String uuid;
        private final String name;

        Tag(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
