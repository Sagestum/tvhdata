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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Reads data from the HTTP API of tvheadend
 */
public class TvhClient {

    private static final Logger logger = Logger.getLogger(TvhClient.class.getName());

    /** connect timeout in ms, the read timeout is a multiple of it, because EPG requests can be large */
    private static final int TIMEOUT = 5000;

    private final String baseUrl;
    private final String user;
    private final String password;

    public TvhClient(String url, String user, String password) {
        this.baseUrl = normalizeUrl(url);
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
    }

    /**
     * Sends a GET request and returns the "entries" of the JSON response, which is the format of all grid requests
     */
    public List<Object> getEntries(String path, Map<String, ?> params) throws IOException {
        String query = encode(params);
        byte[] content = get(path + (query.isEmpty() ? "" : "?" + query));
        Object body = Json.parse(new String(content, StandardCharsets.UTF_8).trim());
        return Json.asList(Json.asMap(body).get("entries"));
    }

    /**
     * @param path path relative to the base URL, e.g. "imagecache/42" or "/api/channel/grid?limit=10"
     */
    public byte[] get(String path) throws IOException {
        String requestUrl = baseUrl + (path.startsWith("/") ? path : "/" + path);
        logger.fine("GET " + requestUrl);
        HttpURLConnection con = (HttpURLConnection) new URL(requestUrl).openConnection();
        try {
            con.setConnectTimeout(TIMEOUT);
            con.setReadTimeout(TIMEOUT * 6);
            if (!user.isEmpty()) {
                // works for basic and digest authentication
                con.setAuthenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password.toCharArray());
                    }
                });
            }
            int code = con.getResponseCode();
            if (code == 401 || code == 403) {
                throw new IOException(TVHData.getTranslation("access_denied", "Access denied. Check user name and password."));
            } else if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " " + con.getResponseMessage() + " (" + requestUrl + ")");
            }
            return readFully(con.getInputStream());
        } finally {
            con.disconnect();
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                bos.write(buffer, 0, length);
            }
            return bos.toByteArray();
        }
    }

    private static String encode(Map<String, ?> params) {
        if (params == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Entry<String, ?> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    static String normalizeUrl(String url) {
        String base = url == null ? "" : url.trim();
        // the user may have copied the URL of the web interface
        if (base.endsWith("/extjs.html")) {
            base = base.substring(0, base.length() - "/extjs.html".length());
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.isEmpty() && !base.contains("://")) {
            base = "http://" + base;
        }
        return base;
    }
}
