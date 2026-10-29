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

import devplugin.Program;

/**
 * Maps the DVB content descriptors (ETSI EN 300 468), which tvheadend delivers as genre, to TV-Browser
 */
public class Genres {

    private static final String[] DEFAULT_NAMES = { null, "Movie/Drama", "News/Current affairs", "Show/Game show", "Sports",
            "Children's/Youth programs", "Music/Ballet/Dance", "Arts/Culture", "Social/Political issues/Economics",
            "Education/Science/Factual topics", "Leisure hobbies", "Special characteristics" };

    private Genres() {}

    /**
     * @param genre content descriptor, e.g. 0x10 for movie
     * @return the Program.INFO_CATEGORIE_* bits for the genre
     */
    public static int getInfoBits(int genre) {
        switch (genre >> 4) {
        case 0x1:
            // 0x15 is soap/melodrama/folklore
            return genre == 0x15 ? Program.INFO_CATEGORIE_SERIES : Program.INFO_CATEGORIE_MOVIE;
        case 0x2:
            // 0x23 is documentary
            return genre == 0x23 ? Program.INFO_CATEGORIE_DOCUMENTARY : Program.INFO_CATEGORIE_NEWS;
        case 0x3:
            return Program.INFO_CATEGORIE_SHOW;
        case 0x4:
            return Program.INFO_CATEGORIE_SPORTS;
        case 0x5:
            return Program.INFO_CATEGORIE_CHILDRENS;
        case 0x6:
        case 0x7:
            return Program.INFO_CATEGORIE_ARTS;
        case 0x8:
        case 0xA:
            return Program.INFO_CATEGORIE_MAGAZINE_INFOTAINMENT;
        case 0x9:
            return Program.INFO_CATEGORIE_DOCUMENTARY;
        case 0xB:
            return Program.INFO_CATEGORIE_OTHERS;
        default:
            return 0;
        }
    }

    /**
     * @return the translated name of the main genre or null, if the genre is unknown
     */
    public static String getName(int genre) {
        int main = genre >> 4;
        if (main <= 0 || main >= DEFAULT_NAMES.length) {
            return null;
        }
        return TVHData.getTranslation("genre." + main, DEFAULT_NAMES[main]);
    }
}
