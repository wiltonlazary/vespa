// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.geo;

/**
 * Utility for parsing one geographical coordinate
 *
 * @author arnej27959
 */
class OneDegreeParser {

    /** The parsed latitude (degrees north if positive). */
    public double latitude = 0;
    public boolean foundLatitude = false;

    /** The parsed longitude (degrees east if positive). */
    public double longitude = 0;
    public boolean foundLongitude = false;

    public static boolean isDigit(char ch) {
        return (ch >= '0' && ch <= '9');
    }
    public static boolean isCompassDirection(char ch) {
        return (ch == 'N' || ch == 'S' || ch == 'E' || ch == 'W');
    }

    private String parseString = null;
    private int len = 0;
    private int pos = 0;

    public String toString() {
        if (foundLatitude) {
            return parseString + " -> latitude(" + latitude + ")";
        } else {
            return parseString + " -> longitude(" + longitude + ")";
        }
    }

    private char getNextChar() throws IllegalArgumentException {
        if (pos == len) {
            pos++;
            return 0;
        } else if (pos > len) {
            throw new IllegalArgumentException("position after end of string when parsing <"+parseString+">");
        } else {
            return parseString.charAt(pos++);
        }
    }

    /**
     * Parse the given string.
     *
     * The string must contain either a latitude or a longitude.
     * A latitude should contain "N" or "S" and a number signifying
     * degrees north or south, or a signed number.
     * A longitude should contain "E" or "W" and a number
     * signifying degrees east or west, or a signed number.
     * <br>
     * Fractional degrees are recommended as the main input format,
     * but degrees plus fractional minutes may be used for testing.
     * You can use the degree sign (U+00B0 as seen in unicode at
     * http://www.unicode.org/charts/PDF/U0080.pdf) to separate
     * degrees from minutes, put the direction (NSEW) between as a
     * separator, or use a small letter 'o' as a replacement for the
     * degrees sign.
     * <br>
     * Some valid input formats: <br>
     * "37.416383" and "-122.024683" → Sunnyvale <br>
     * "N37.416383" and "W122.024683" → Sunnyvale <br>
     * "37N24.983" and "122W01.481" → same <br>
     * "N37\u00B024.983" and "W122\u00B001.481" → same <br>
     * "63.418417" and "10.433033" → Trondheim <br>
     * "N63.418417" and "E10.433033" → same <br>
     * "N63o25.105" and "E10o25.982" → same <br>
     * "E10o25.982" and "N63o25.105" → same <br>
     * "N63.418417" and "E10.433033" → same <br>
     * "63N25.105" and "10E25.982" → same <br>
     *
     * @param assumeNorthSouth Latitude assumed, otherwise longitude
     * @param toParse Latitude or longitude string to parse
     */
    public OneDegreeParser(boolean assumeNorthSouth, String toParse) throws IllegalArgumentException {
        this.parseString = toParse;
        this.len = parseString.length();
        consumeString(assumeNorthSouth);
    }

    private void consumeString(boolean assumeNorthSouth)  throws IllegalArgumentException {
        char ch = getNextChar();

        double degrees = 0.0;
        double minutes = 0.0;
        double seconds = 0.0;
        boolean degSet = false;
        boolean minSet = false;
        boolean secSet = false;
        boolean dirSet = false;
        boolean foundDot = false;
        boolean foundDigits = false;

        boolean findingLatitude = false;
        boolean findingLongitude = false;

        double sign = +1.0;

        int lastpos = -1;

        // sign must be first character in string if present:
        if (ch == '+') {
            // unary plus is a nop
            ch = getNextChar();
        } else if (ch == '-') {
            sign = -1.0;
            ch = getNextChar();
        }
        do {
            // did we find a valid char?
            boolean valid = false;
            if (pos == lastpos) {
                throw new IllegalArgumentException("internal logic error at <"+parseString+"> pos:"+pos);
            } else {
                lastpos = pos;
            }

            // first, see if we can find some number
            double accum = 0.0;

            if (isDigit(ch) || ch == '.') {
                valid = true;
                double divider = 1.0;
                foundDot = false;
                while (isDigit(ch)) {
                    foundDigits = true;
                    accum *= 10;
                    accum += (ch - '0');
                    ch = getNextChar();
                }
                if (ch == '.') {
                    foundDot = true;
                    ch = getNextChar();
                    while (isDigit(ch)) {
                        foundDigits = true;
                        accum *= 10;
                        accum += (ch - '0');
                        divider *= 10;
                        ch = getNextChar();
                    }
                }
                if (!foundDigits) {
                    throw new IllegalArgumentException("just a . is not a valid number when parsing <"+parseString+">");
                }
                accum /= divider;
            }

            // next, did we find a separator after the number?
            // degree sign is a separator after degrees, before minutes
            if (ch == '\u00B0' || ch == 'o') {
                valid = true;
                if (degSet) {
                    throw new IllegalArgumentException("degrees sign only valid just after degrees when parsing <"+parseString+">");
                }
                if (!foundDigits) {
                    throw new IllegalArgumentException("must have number before degrees sign when parsing <"+parseString+">");
                }
                if (foundDot) {
                    throw new IllegalArgumentException("cannot have fractional degrees before degrees sign when parsing <"+parseString+">");
                }
                ch = getNextChar();
            }
            // apostrophe is a separator after minutes, before seconds
            if (ch == '\'') {
                if (minSet || !degSet || !foundDigits) {
                    throw new IllegalArgumentException("minutes sign only valid just after minutes when parsing <"+parseString+">");
                }
                if (foundDot) {
                    throw new IllegalArgumentException("cannot have fractional minutes before minutes sign when parsing <"+parseString+">");
                }
                ch = getNextChar();
            }

            // if we found some number, assign it into the next unset variable
            if (foundDigits) {
                valid = true;
                if (degSet) {
                    if (minSet) {
                        if (secSet) {
                            throw new IllegalArgumentException("extra number after full field when parsing <"+parseString+">");
                        } else {
                            seconds = accum;
                            secSet = true;
                        }
                    } else {
                        minutes = accum;
                        minSet = true;
                        if (foundDot) {
                            secSet = true;
                        }
                    }
                } else {
                    degrees = accum;
                    degSet = true;
                    if (foundDot) {
                        minSet = true;
                        secSet = true;
                    }
                }
                foundDot = false;
                foundDigits = false;
            }

            // there may to be a direction (NSEW) somewhere, too
            if (isCompassDirection(ch)) {
                valid = true;
                if (dirSet) {
                    throw new IllegalArgumentException("already set direction once, cannot add direction: "+ch+" when parsing <"+parseString+">");
                }
                dirSet = true;
                if (ch == 'S' || ch == 'W') {
                    sign = -1;
                } else {
                    sign = 1;
                }
                if (ch == 'E' || ch == 'W') {
                    findingLongitude = true;
                } else {
                    findingLatitude = true;
                }
                ch = getNextChar();
            }

            // lastly, did we find the end-of-string?
            if (ch == 0) {
                valid = true;
                if (!dirSet) {
                    if (assumeNorthSouth) {
                        findingLatitude = true;
                    } else {
                        findingLongitude = true;
                    }
                }
                if (!degSet) {
                    throw new IllegalArgumentException("end of field without any number seen when parsing <"+parseString+">");
                }
                degrees += minutes / 60.0;
                degrees += seconds / 3600.0;
                degrees *= sign;

                if (findingLatitude) {
                    if (degrees < -90.0 || degrees > 90.0) {
                        throw new IllegalArgumentException("out of range [-90,+90]: "+degrees+" when parsing <"+parseString+">");
                    }
                    latitude = degrees;
                    foundLatitude = true;
                } else if (findingLongitude) {
                    if (degrees < -180.0 || degrees > 180.0) {
                        throw new IllegalArgumentException("out of range [-180,+180]: "+degrees+" when parsing <"+parseString+">");
                    }
                    longitude = degrees;
                    foundLongitude = true;
                }
                break;
            }
            if (!valid) {
                throw new IllegalArgumentException("invalid character: "+ch+" when parsing <"+parseString+">");
            }
        } while (ch != 0);
        // everything parsed OK
        if (foundLatitude && foundLongitude) {
            throw new IllegalArgumentException("found both latitude and longitude from: "+parseString);
        }
        if (foundLatitude || foundLongitude) {
            return;
        }
        throw new IllegalArgumentException("found neither latitude nor longitude from: "+parseString);
    }

}
