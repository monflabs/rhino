package org.monflabs.mozilla.javascript;

/**
 * The identifier characters of UAX #31, which ECMAScript uses (12.7 Names and Keywords), derived
 * from the JDK's.
 */
public final class UnicodeIdentifiers {

    private UnicodeIdentifiers() {}

    /**
     * Whether {@code c} has the Unicode property ID_Start. The JDK's identifier characters include
     * Pattern_Syntax and Pattern_White_Space, which UAX #31 excludes.
     */
    public static boolean isIdStart(int c) {
        if (c < 0x80) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }
        return Character.isUnicodeIdentifierStart(c)
                && !isPatternSyntax(c)
                && !isPatternWhiteSpace(c);
    }

    /**
     * Whether {@code c} has the Unicode property ID_Continue. The JDK's identifier parts also
     * include the ignorable characters, of which only ZWNJ and ZWJ are ID_Continue
     * (Other_ID_Continue).
     */
    public static boolean isIdContinue(int c) {
        if (c < 0x80) {
            return (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
        }
        return Character.isUnicodeIdentifierPart(c)
                && !isPatternSyntax(c)
                && !isPatternWhiteSpace(c)
                && (!Character.isIdentifierIgnorable(c) || c == 0x200C || c == 0x200D);
    }

    /** Whether {@code c} has the Unicode property Pattern_Syntax, a fixed set. */
    public static boolean isPatternSyntax(int c) {
        return in(
                c, 0x21, 0x2F, 0x3A, 0x40, 0x5B, 0x5E, 0x60, 0x60, 0x7B, 0x7E, 0xA1, 0xA7, 0xA9,
                0xA9, 0xAB, 0xAC, 0xAE, 0xAE, 0xB0, 0xB1, 0xB6, 0xB6, 0xBB, 0xBB, 0xBF, 0xBF, 0xD7,
                0xD7, 0xF7, 0xF7, 0x2010, 0x2027, 0x2030, 0x203E, 0x2041, 0x2053, 0x2055, 0x205E,
                0x2190, 0x245F, 0x2500, 0x2775, 0x2794, 0x2BFF, 0x2E00, 0x2E7F, 0x3001, 0x3003,
                0x3008, 0x3020, 0x3030, 0x3030, 0xFD3E, 0xFD3F, 0xFE45, 0xFE46);
    }

    /** Whether {@code c} has the Unicode property Pattern_White_Space, a fixed set. */
    public static boolean isPatternWhiteSpace(int c) {
        return in(c, 0x09, 0x0D, 0x20, 0x20, 0x85, 0x85, 0x200E, 0x200F, 0x2028, 0x2029);
    }

    /** Whether {@code c} is in one of the inclusive ranges {@code ranges}, given as pairs. */
    private static boolean in(int c, int... ranges) {
        for (int i = 0; i < ranges.length; i += 2) {
            if (c >= ranges[i] && c <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }
}
