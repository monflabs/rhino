package org.monflabs.mozilla.javascript.regexp;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;

/**
 * Unicode properties handler for Java 11 Character class. Handles binary properties from ECMA-262
 * and general category values.
 */
public class UnicodeProperties {
    // Binary Property Names (from ECMA-262 table-binary-unicode-properties)
    public static final byte ALPHABETIC = 1;
    public static final byte ASCII = ALPHABETIC + 1;
    public static final byte CASE_IGNORABLE = ASCII + 1;
    public static final byte ASCII_HEX_DIGIT = CASE_IGNORABLE + 1;
    public static final byte HEX_DIGIT = ASCII_HEX_DIGIT + 1;
    public static final byte ID_CONTINUE = HEX_DIGIT + 1;
    public static final byte ID_START = ID_CONTINUE + 1;
    public static final byte LOWERCASE = ID_START + 1;
    public static final byte UPPERCASE = LOWERCASE + 1;
    public static final byte WHITE_SPACE = UPPERCASE + 1;

    // Non-binary properties
    public static final byte GENERAL_CATEGORY = WHITE_SPACE + 1;
    public static final byte SCRIPT = GENERAL_CATEGORY + 1;

    // The properties resolved by lookup(String, true), under VERSION_ECMASCRIPT
    private static final byte ES_GENERAL_CATEGORY = 0x40;
    private static final byte ES_BINARY = ES_GENERAL_CATEGORY + 1;

    // Property Values for General Category (from PropertyValueAliases.txt)
    // OTHER
    public static final byte OTHER = 1;
    public static final byte CONTROL = OTHER + 1;
    public static final byte FORMAT = CONTROL + 1;
    public static final byte UNASSIGNED = FORMAT + 1;
    public static final byte PRIVATE_USE = UNASSIGNED + 1;
    public static final byte SURROGATE = PRIVATE_USE + 1;
    public static final byte LETTER = SURROGATE + 1;
    public static final byte LOWERCASE_LETTER = LETTER + 1;
    public static final byte MODIFIER_LETTER = LOWERCASE_LETTER + 1;
    public static final byte OTHER_LETTER = MODIFIER_LETTER + 1;
    public static final byte TITLECASE_LETTER = OTHER_LETTER + 1;
    public static final byte UPPERCASE_LETTER = TITLECASE_LETTER + 1;
    public static final byte MARK = UPPERCASE_LETTER + 1;
    public static final byte SPACING_MARK = MARK + 1;
    public static final byte ENCLOSING_MARK = SPACING_MARK + 1;
    public static final byte NONSPACING_MARK = ENCLOSING_MARK + 1;
    public static final byte NUMBER = NONSPACING_MARK + 1;
    public static final byte DECIMAL_NUMBER = NUMBER + 1;
    public static final byte LETTER_NUMBER = DECIMAL_NUMBER + 1;
    public static final byte OTHER_NUMBER = LETTER_NUMBER + 1;
    public static final byte PUNCTUATION = OTHER_NUMBER + 1;
    public static final byte CONNECTOR_PUNCTUATION = PUNCTUATION + 1;
    public static final byte DASH_PUNCTUATION = CONNECTOR_PUNCTUATION + 1;
    public static final byte CLOSE_PUNCTUATION = DASH_PUNCTUATION + 1;
    public static final byte FINAL_PUNCTUATION = CLOSE_PUNCTUATION + 1;
    public static final byte INITIAL_PUNCTUATION = FINAL_PUNCTUATION + 1;
    public static final byte OTHER_PUNCTUATION = INITIAL_PUNCTUATION + 1;
    public static final byte OPEN_PUNCTUATION = OTHER_PUNCTUATION + 1;
    public static final byte SYMBOL = OPEN_PUNCTUATION + 1;
    public static final byte CURRENCY_SYMBOL = SYMBOL + 1;
    public static final byte MODIFIER_SYMBOL = CURRENCY_SYMBOL + 1;
    public static final byte MATH_SYMBOL = MODIFIER_SYMBOL + 1;
    public static final byte OTHER_SYMBOL = MATH_SYMBOL + 1;
    public static final byte SEPARATOR = OTHER_SYMBOL + 1;
    public static final byte LINE_SEPARATOR = SEPARATOR + 1;
    public static final byte PARAGRAPH_SEPARATOR = LINE_SEPARATOR + 1;
    public static final byte SPACE_SEPARATOR = PARAGRAPH_SEPARATOR + 1;

    // Binary property values
    public static final byte TRUE = SPACE_SEPARATOR + 1;
    public static final byte FALSE = TRUE + 1;

    // Property Name Map (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_NAMES =
            Map.ofEntries(
                    Map.entry("Alphabetic", ALPHABETIC),
                    Map.entry("Alpha", ALPHABETIC),
                    Map.entry("ASCII", ASCII),
                    Map.entry("Case_Ignorable", CASE_IGNORABLE),
                    Map.entry("CI", CASE_IGNORABLE),
                    Map.entry("General_Category", GENERAL_CATEGORY),
                    Map.entry("gc", GENERAL_CATEGORY),
                    Map.entry("Script", SCRIPT),
                    Map.entry("sc", SCRIPT),
                    Map.entry("ASCII_Hex_Digit", ASCII_HEX_DIGIT),
                    Map.entry("AHex", ASCII_HEX_DIGIT),
                    Map.entry("Hex_Digit", HEX_DIGIT),
                    Map.entry("Hex", HEX_DIGIT),
                    Map.entry("ID_Continue", ID_CONTINUE),
                    Map.entry("IDC", ID_CONTINUE),
                    Map.entry("ID_Start", ID_START),
                    Map.entry("IDS", ID_START),
                    Map.entry("Lowercase", LOWERCASE),
                    Map.entry("Lower", LOWERCASE),
                    Map.entry("Uppercase", UPPERCASE),
                    Map.entry("Upper", UPPERCASE),
                    Map.entry("White_Space", WHITE_SPACE),
                    Map.entry("space", WHITE_SPACE));

    // Property Value Map for General Category (canonical names and aliases)
    public static final Map<String, Byte> PROPERTY_VALUES =
            Map.<String, Byte>ofEntries(
                    Map.entry("Other", OTHER),
                    Map.entry("C", OTHER),
                    Map.entry("Control", CONTROL),
                    Map.entry("Cc", CONTROL),
                    Map.entry("cntrl", CONTROL),
                    Map.entry("Format", FORMAT),
                    Map.entry("Cf", FORMAT),
                    Map.entry("Unassigned", UNASSIGNED),
                    Map.entry("Cn", UNASSIGNED),
                    Map.entry("Private_Use", PRIVATE_USE),
                    Map.entry("Co", PRIVATE_USE),
                    Map.entry("Surrogate", SURROGATE),
                    Map.entry("Cs", SURROGATE),
                    Map.entry("Letter", LETTER),
                    Map.entry("L", LETTER),
                    Map.entry("Lowercase_Letter", LOWERCASE_LETTER),
                    Map.entry("Ll", LOWERCASE_LETTER),
                    Map.entry("Modifier_Letter", MODIFIER_LETTER),
                    Map.entry("Lm", MODIFIER_LETTER),
                    Map.entry("Other_Letter", OTHER_LETTER),
                    Map.entry("Lo", OTHER_LETTER),
                    Map.entry("Titlecase_Letter", TITLECASE_LETTER),
                    Map.entry("Lt", TITLECASE_LETTER),
                    Map.entry("Uppercase_Letter", UPPERCASE_LETTER),
                    Map.entry("Lu", UPPERCASE_LETTER),
                    Map.entry("Mark", MARK),
                    Map.entry("M", MARK),
                    Map.entry("Combining_Mark", MARK),
                    Map.entry("Spacing_Mark", SPACING_MARK),
                    Map.entry("Mc", SPACING_MARK),
                    Map.entry("Enclosing_Mark", ENCLOSING_MARK),
                    Map.entry("Me", ENCLOSING_MARK),
                    Map.entry("Nonspacing_Mark", NONSPACING_MARK),
                    Map.entry("Mn", NONSPACING_MARK),
                    Map.entry("Number", NUMBER),
                    Map.entry("N", NUMBER),
                    Map.entry("Decimal_Number", DECIMAL_NUMBER),
                    Map.entry("Nd", DECIMAL_NUMBER),
                    Map.entry("digit", NUMBER),
                    Map.entry("Letter_Number", LETTER_NUMBER),
                    Map.entry("Nl", LETTER_NUMBER),
                    Map.entry("Other_Number", OTHER_NUMBER),
                    Map.entry("No", OTHER_NUMBER),
                    Map.entry("Punctuation", PUNCTUATION),
                    Map.entry("P", PUNCTUATION),
                    Map.entry("punct", PUNCTUATION),
                    Map.entry("Connector_Punctuation", CONNECTOR_PUNCTUATION),
                    Map.entry("Pc", CONNECTOR_PUNCTUATION),
                    Map.entry("Dash_Punctuation", DASH_PUNCTUATION),
                    Map.entry("Pd", DASH_PUNCTUATION),
                    Map.entry("Close_Punctuation", CLOSE_PUNCTUATION),
                    Map.entry("Pe", CLOSE_PUNCTUATION),
                    Map.entry("Final_Punctuation", FINAL_PUNCTUATION),
                    Map.entry("Pf", FINAL_PUNCTUATION),
                    Map.entry("Initial_Punctuation", INITIAL_PUNCTUATION),
                    Map.entry("Pi", INITIAL_PUNCTUATION),
                    Map.entry("Other_Punctuation", OTHER_PUNCTUATION),
                    Map.entry("Po", OTHER_PUNCTUATION),
                    Map.entry("Open_Punctuation", OPEN_PUNCTUATION),
                    Map.entry("Ps", OPEN_PUNCTUATION),
                    Map.entry("Symbol", SYMBOL),
                    Map.entry("S", SYMBOL),
                    Map.entry("Currency_Symbol", CURRENCY_SYMBOL),
                    Map.entry("Sc", CURRENCY_SYMBOL),
                    Map.entry("Modifier_Symbol", MODIFIER_SYMBOL),
                    Map.entry("Sk", MODIFIER_SYMBOL),
                    Map.entry("Math_Symbol", MATH_SYMBOL),
                    Map.entry("Sm", MATH_SYMBOL),
                    Map.entry("Other_Symbol", OTHER_SYMBOL),
                    Map.entry("So", OTHER_SYMBOL),
                    Map.entry("Separator", SEPARATOR),
                    Map.entry("Z", SEPARATOR),
                    Map.entry("Line_Separator", LINE_SEPARATOR),
                    Map.entry("Zl", LINE_SEPARATOR),
                    Map.entry("Paragraph_Separator", PARAGRAPH_SEPARATOR),
                    Map.entry("Zp", PARAGRAPH_SEPARATOR),
                    Map.entry("Space_Separator", SPACE_SEPARATOR),
                    Map.entry("Zs", SPACE_SEPARATOR));

    /**
     * Looks up a property name and optionally a value and returns an encoded int. For binary
     * properties, combines the property name with TRUE. For General_Category, combines
     * General_Category with the specified value.
     *
     * @param propertyOrValue Property name or property name=value pair
     * @return Encoded int combining property name and value
     */
    @SuppressWarnings("EnumOrdinal") // We don't persist the ordinals; hence this is safe.
    public static int lookup(String propertyOrValue) {
        if (propertyOrValue == null || propertyOrValue.isEmpty()) {
            return -1;
        }

        Matcher m =
                java.util.regex.Pattern.compile(
                                "^(?<propName>[a-zA-Z_]+)(?:=(?<propValue>[a-zA-Z_0-9]+))?$")
                        .matcher(propertyOrValue);
        m.find();
        if (!m.matches() || m.group("propName") == null) {
            return -1;
        }

        if (m.group("propValue") == null) {
            // It's a single property name (binary property)
            String property = m.group("propName");

            Byte propByte = PROPERTY_NAMES.get(property);

            if (propByte == null) {
                // Check if it's a general category value without the gc= prefix
                Byte valueByte = PROPERTY_VALUES.get(property);
                if (valueByte != null) {
                    // It's a GC value, encode it with GC property
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                }
                return -1;
            }

            if (propByte == GENERAL_CATEGORY || propByte == SCRIPT) {
                return -1;
            }

            // It's a binary property, encode with TRUE
            return encodeProperty(propByte, TRUE);
        } else {
            // It's a property=value format
            String property = m.group("propName");
            String value = m.group("propValue");

            Byte propByte = PROPERTY_NAMES.get(property);
            if (propByte == null) {
                return -1;
            }

            switch (propByte) {
                case GENERAL_CATEGORY:
                    Byte valueByte = PROPERTY_VALUES.get(value);
                    if (valueByte == null) {
                        return -1;
                    }
                    return encodeProperty(GENERAL_CATEGORY, valueByte);
                case SCRIPT:
                    try {
                        return encodeProperty(
                                SCRIPT, (byte) Character.UnicodeScript.forName(value).ordinal());
                    } catch (IllegalArgumentException e) {
                        return -1;
                    }
                default:
                    // Binary properties don't have values
                    return -1;
            }
        }
    }

    /**
     * Looks up the body of a property escape. Under VERSION_ECMASCRIPT ({@code ecmaScript}), the
     * names, aliases and properties are those of ECMA-262 (table-nonbinary-unicode-properties and
     * table-binary-unicode-properties), matched exactly; the binary properties that cannot be
     * derived from the JDK are not supported (decision 4.3). Otherwise, the lookup of {@link
     * #lookup(String)}.
     *
     * @return the encoded property, or -1 if it is not a supported property
     */
    @SuppressWarnings("EnumOrdinal") // We don't persist the ordinals; hence this is safe.
    public static int lookup(String propertyOrValue, boolean ecmaScript) {
        if (!ecmaScript) {
            return lookup(propertyOrValue);
        }
        int eq = propertyOrValue.indexOf('=');
        if (eq < 0) {
            Integer gc = ES_GENERAL_CATEGORY_VALUES.get(propertyOrValue);
            if (gc != null) {
                return encodeProperty(ES_GENERAL_CATEGORY, (byte) gc.intValue());
            }
            Binary binary = Binary.BY_NAME.get(propertyOrValue);
            return binary == null ? -1 : encodeProperty(ES_BINARY, (byte) binary.ordinal());
        }
        String name = propertyOrValue.substring(0, eq);
        String value = propertyOrValue.substring(eq + 1);
        switch (name) {
            case "General_Category":
            case "gc":
                Integer gc = ES_GENERAL_CATEGORY_VALUES.get(value);
                return gc == null ? -1 : encodeProperty(ES_GENERAL_CATEGORY, (byte) gc.intValue());
            case "Script":
            case "sc":
                Character.UnicodeScript script = script(value);
                return script == null ? -1 : encodeProperty(SCRIPT, (byte) script.ordinal());
            default:
                return -1;
        }
    }

    private static Character.UnicodeScript script(String value) {
        switch (value) {
            case "Qaac":
                return Character.UnicodeScript.COPTIC;
            case "Qaai":
                return Character.UnicodeScript.INHERITED;
            default:
                Character.UnicodeScript script;
                try {
                    script = Character.UnicodeScript.forName(value);
                } catch (IllegalArgumentException e) {
                    return null;
                }
                // forName ignores case, which ECMAScript does not: the value must be the Unicode
                // name or the ISO 15924 code, as spelled in PropertyValueAliases.txt
                return value.equals(unicodeName(script)) || isIsoCode(value) ? script : null;
        }
    }

    private static String unicodeName(Character.UnicodeScript script) {
        if (script == Character.UnicodeScript.SIGNWRITING) {
            return "SignWriting";
        }
        char[] name = script.name().toLowerCase(Locale.ROOT).toCharArray();
        for (int i = 0; i < name.length; i++) {
            if (i == 0 || name[i - 1] == '_') {
                name[i] = Character.toUpperCase(name[i]);
            }
        }
        return new String(name);
    }

    private static boolean isIsoCode(String value) {
        if (value.length() != 4 || value.charAt(0) < 'A' || value.charAt(0) > 'Z') {
            return false;
        }
        for (int i = 1; i < 4; i++) {
            if (value.charAt(i) < 'a' || value.charAt(i) > 'z') {
                return false;
            }
        }
        return true;
    }

    private static final int LU = 1 << Character.UPPERCASE_LETTER;
    private static final int LL = 1 << Character.LOWERCASE_LETTER;
    private static final int LT = 1 << Character.TITLECASE_LETTER;
    private static final int LM = 1 << Character.MODIFIER_LETTER;
    private static final int LO = 1 << Character.OTHER_LETTER;
    private static final int MN = 1 << Character.NON_SPACING_MARK;
    private static final int MC = 1 << Character.COMBINING_SPACING_MARK;
    private static final int ME = 1 << Character.ENCLOSING_MARK;
    private static final int ND = 1 << Character.DECIMAL_DIGIT_NUMBER;
    private static final int NL = 1 << Character.LETTER_NUMBER;
    private static final int NO = 1 << Character.OTHER_NUMBER;
    private static final int PC = 1 << Character.CONNECTOR_PUNCTUATION;
    private static final int PD = 1 << Character.DASH_PUNCTUATION;
    private static final int PS = 1 << Character.START_PUNCTUATION;
    private static final int PE = 1 << Character.END_PUNCTUATION;
    private static final int PI = 1 << Character.INITIAL_QUOTE_PUNCTUATION;
    private static final int PF = 1 << Character.FINAL_QUOTE_PUNCTUATION;
    private static final int PO = 1 << Character.OTHER_PUNCTUATION;
    private static final int SM = 1 << Character.MATH_SYMBOL;
    private static final int SC = 1 << Character.CURRENCY_SYMBOL;
    private static final int SK = 1 << Character.MODIFIER_SYMBOL;
    private static final int SO = 1 << Character.OTHER_SYMBOL;
    private static final int ZS = 1 << Character.SPACE_SEPARATOR;
    private static final int ZL = 1 << Character.LINE_SEPARATOR;
    private static final int ZP = 1 << Character.PARAGRAPH_SEPARATOR;
    private static final int CC = 1 << Character.CONTROL;
    private static final int CF = 1 << Character.FORMAT;
    private static final int CS = 1 << Character.SURROGATE;
    private static final int CO = 1 << Character.PRIVATE_USE;
    private static final int CN = 1 << Character.UNASSIGNED;

    /** The General_Category values, as masks of {@link Character#getType} values. */
    private static final int[] ES_GENERAL_CATEGORY_MASKS = {
        LU | LL | LT | LM | LO,
        LU | LL | LT,
        LU,
        LL,
        LT,
        LM,
        LO,
        MN | MC | ME,
        MN,
        MC,
        ME,
        ND | NL | NO,
        ND,
        NL,
        NO,
        PC | PD | PS | PE | PI | PF | PO,
        PC,
        PD,
        PS,
        PE,
        PI,
        PF,
        PO,
        SM | SC | SK | SO,
        SM,
        SC,
        SK,
        SO,
        ZS | ZL | ZP,
        ZS,
        ZL,
        ZP,
        CC | CF | CS | CO | CN,
        CC,
        CF,
        CS,
        CO,
        CN
    };

    /** The General_Category names and aliases (PropertyValueAliases.txt) to their mask index. */
    private static final Map<String, Integer> ES_GENERAL_CATEGORY_VALUES = new HashMap<>();

    static {
        String[][] names = {
            {"Letter", "L"},
            {"Cased_Letter", "LC"},
            {"Uppercase_Letter", "Lu"},
            {"Lowercase_Letter", "Ll"},
            {"Titlecase_Letter", "Lt"},
            {"Modifier_Letter", "Lm"},
            {"Other_Letter", "Lo"},
            {"Mark", "M", "Combining_Mark"},
            {"Nonspacing_Mark", "Mn"},
            {"Spacing_Mark", "Mc"},
            {"Enclosing_Mark", "Me"},
            {"Number", "N"},
            {"Decimal_Number", "Nd", "digit"},
            {"Letter_Number", "Nl"},
            {"Other_Number", "No"},
            {"Punctuation", "P", "punct"},
            {"Connector_Punctuation", "Pc"},
            {"Dash_Punctuation", "Pd"},
            {"Open_Punctuation", "Ps"},
            {"Close_Punctuation", "Pe"},
            {"Initial_Punctuation", "Pi"},
            {"Final_Punctuation", "Pf"},
            {"Other_Punctuation", "Po"},
            {"Symbol", "S"},
            {"Math_Symbol", "Sm"},
            {"Currency_Symbol", "Sc"},
            {"Modifier_Symbol", "Sk"},
            {"Other_Symbol", "So"},
            {"Separator", "Z"},
            {"Space_Separator", "Zs"},
            {"Line_Separator", "Zl"},
            {"Paragraph_Separator", "Zp"},
            {"Other", "C"},
            {"Control", "Cc", "cntrl"},
            {"Format", "Cf"},
            {"Surrogate", "Cs"},
            {"Private_Use", "Co"},
            {"Unassigned", "Cn"}
        };
        for (int i = 0; i < names.length; i++) {
            for (String name : names[i]) {
                ES_GENERAL_CATEGORY_VALUES.put(name, i);
            }
        }
    }

    /**
     * The binary properties of ECMA-262 that are derived from the JDK: its character properties,
     * case mappings and normalization, and the code point sets that the Unicode stability policy
     * keeps fixed.
     */
    private enum Binary {
        ASCII(c -> c <= 0x7F),
        ASCII_Hex_Digit("AHex", UnicodeProperties::isHexDigit),
        Alphabetic("Alpha", Character::isAlphabetic),
        Any(c -> true),
        Assigned(c -> Character.getType(c) != Character.UNASSIGNED),
        Bidi_Control(
                "Bidi_C",
                c -> in(c, 0x061C, 0x061C, 0x200E, 0x200F, 0x202A, 0x202E, 0x2066, 0x2069)),
        Bidi_Mirrored("Bidi_M", Character::isMirrored),
        Case_Ignorable("CI", UnicodeProperties::isCaseIgnorable),
        Cased(
                c ->
                        Character.isLowerCase(c)
                                || Character.isUpperCase(c)
                                || Character.isTitleCase(c)),
        Changes_When_Casefolded("CWCF", c -> changesWhenCasefolded(c)),
        Changes_When_Casemapped(
                "CWCM",
                c ->
                        changesWhenLowercased(c)
                                || changesWhenUppercased(c)
                                || changesWhenTitlecased(c)),
        Changes_When_Lowercased("CWL", UnicodeProperties::changesWhenLowercased),
        Changes_When_NFKC_Casefolded("CWKCF", UnicodeProperties::changesWhenNfkcCasefolded),
        Changes_When_Titlecased("CWT", UnicodeProperties::changesWhenTitlecased),
        Changes_When_Uppercased("CWU", UnicodeProperties::changesWhenUppercased),
        Default_Ignorable_Code_Point("DI", UnicodeProperties::isDefaultIgnorable),
        Deprecated(
                "Dep",
                c ->
                        in(
                                c, 0x0149, 0x0149, 0x0673, 0x0673, 0x0F77, 0x0F77, 0x0F79, 0x0F79,
                                0x17A3, 0x17A4, 0x206A, 0x206F, 0x2329, 0x232A, 0xE0001, 0xE0001)),
        Emoji(Character::isEmoji),
        Emoji_Component("EComp", Character::isEmojiComponent),
        Emoji_Modifier("EMod", Character::isEmojiModifier),
        Emoji_Modifier_Base("EBase", Character::isEmojiModifierBase),
        Emoji_Presentation("EPres", Character::isEmojiPresentation),
        Extended_Pictographic("ExtPict", Character::isExtendedPictographic),
        Hex_Digit(
                "Hex", c -> isHexDigit(c) || in(c, 0xFF10, 0xFF19, 0xFF21, 0xFF26, 0xFF41, 0xFF46)),
        ID_Continue("IDC", UnicodeProperties::isIdContinue),
        ID_Start("IDS", UnicodeProperties::isIdStart),
        Ideographic("Ideo", Character::isIdeographic),
        Join_Control("Join_C", c -> c == 0x200C || c == 0x200D),
        Logical_Order_Exception(
                "LOE",
                c ->
                        in(
                                c, 0x0E40, 0x0E44, 0x0EC0, 0x0EC4, 0x19B5, 0x19B7, 0x19BA, 0x19BA,
                                0xAAB5, 0xAAB6, 0xAAB9, 0xAAB9, 0xAABB, 0xAABC)),
        Lowercase("Lower", Character::isLowerCase),
        Noncharacter_Code_Point("NChar", c -> in(c, 0xFDD0, 0xFDEF) || (c & 0xFFFE) == 0xFFFE),
        Pattern_Syntax("Pat_Syn", UnicodeProperties::isPatternSyntax),
        Pattern_White_Space("Pat_WS", UnicodeProperties::isPatternWhiteSpace),
        Radical(c -> in(c, 0x2E80, 0x2E99, 0x2E9B, 0x2EF3, 0x2F00, 0x2FD5)),
        Regional_Indicator("RI", c -> in(c, 0x1F1E6, 0x1F1FF)),
        Unified_Ideograph("UIdeo", UnicodeProperties::isUnifiedIdeograph),
        Uppercase("Upper", Character::isUpperCase),
        Variation_Selector("VS", UnicodeProperties::isVariationSelector),
        White_Space(
                "space",
                c ->
                        in(
                                c, 0x09, 0x0D, 0x20, 0x20, 0x85, 0x85, 0xA0, 0xA0, 0x1680, 0x1680,
                                0x2000, 0x200A, 0x2028, 0x2029, 0x202F, 0x202F, 0x205F, 0x205F,
                                0x3000, 0x3000)),
        XID_Continue("XIDC", c -> isIdContinue(c) && !isNotXid(c)),
        XID_Start(
                "XIDS",
                c ->
                        isIdStart(c)
                                && !isNotXid(c)
                                && !in(c, 0x0E33, 0x0E33, 0x0EB3, 0x0EB3, 0xFF9E, 0xFF9F));

        private static final Map<String, Binary> BY_NAME = new HashMap<>();

        static {
            for (Binary b : values()) {
                BY_NAME.put(b.name(), b);
                if (b.alias != null) {
                    BY_NAME.put(b.alias, b);
                }
            }
        }

        private static final Binary[] VALUES = values();

        final String alias;
        final IntPredicate predicate;

        Binary(IntPredicate predicate) {
            this(null, predicate);
        }

        Binary(String alias, IntPredicate predicate) {
            this.alias = alias;
            this.predicate = predicate;
        }
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

    private static boolean isPatternSyntax(int c) {
        return in(
                c, 0x21, 0x2F, 0x3A, 0x40, 0x5B, 0x5E, 0x60, 0x60, 0x7B, 0x7E, 0xA1, 0xA7, 0xA9,
                0xA9, 0xAB, 0xAC, 0xAE, 0xAE, 0xB0, 0xB1, 0xB6, 0xB6, 0xBB, 0xBB, 0xBF, 0xBF, 0xD7,
                0xD7, 0xF7, 0xF7, 0x2010, 0x2027, 0x2030, 0x203E, 0x2041, 0x2053, 0x2055, 0x205E,
                0x2190, 0x245F, 0x2500, 0x2775, 0x2794, 0x2BFF, 0x2E00, 0x2E7F, 0x3001, 0x3003,
                0x3008, 0x3020, 0x3030, 0x3030, 0xFD3E, 0xFD3F, 0xFE45, 0xFE46);
    }

    private static boolean isPatternWhiteSpace(int c) {
        return in(c, 0x09, 0x0D, 0x20, 0x20, 0x85, 0x85, 0x200E, 0x200F, 0x2028, 0x2029);
    }

    private static boolean isVariationSelector(int c) {
        return in(c, 0x180B, 0x180D, 0x180F, 0x180F, 0xFE00, 0xFE0F, 0xE0100, 0xE01EF);
    }

    // The JDK's identifier characters include Pattern_Syntax and Pattern_White_Space, which UAX #31
    // excludes, and the ignorable characters, except ZWNJ and ZWJ (Other_ID_Continue).
    private static boolean isIdStart(int c) {
        return Character.isUnicodeIdentifierStart(c)
                && !isPatternSyntax(c)
                && !isPatternWhiteSpace(c);
    }

    private static boolean isIdContinue(int c) {
        return Character.isUnicodeIdentifierPart(c)
                && !isPatternSyntax(c)
                && !isPatternWhiteSpace(c)
                && (!Character.isIdentifierIgnorable(c) || c == 0x200C || c == 0x200D);
    }

    /** The identifier characters that are not stable under NFKC, removed from XID_*. */
    private static boolean isNotXid(int c) {
        return in(
                c, 0x037A, 0x037A, 0x309B, 0x309C, 0xFC5E, 0xFC63, 0xFDFA, 0xFDFB, 0xFE70, 0xFE70,
                0xFE72, 0xFE72, 0xFE74, 0xFE74, 0xFE76, 0xFE76, 0xFE78, 0xFE78, 0xFE7A, 0xFE7A,
                0xFE7C, 0xFE7C, 0xFE7E, 0xFE7E);
    }

    // Mn, Me, Cf, Lm, Sk, and Word_Break MidLetter, MidNumLet and Single_Quote
    private static boolean isCaseIgnorable(int c) {
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT
                || type == Character.MODIFIER_LETTER
                || type == Character.MODIFIER_SYMBOL
                || in(
                        c, 0x27, 0x27, 0x2E, 0x2E, 0x3A, 0x3A, 0xB7, 0xB7, 0x0387, 0x0387, 0x055F,
                        0x055F, 0x05F4, 0x05F4, 0x2018, 0x2019, 0x2024, 0x2024, 0x2027, 0x2027,
                        0xFE13, 0xFE13, 0xFE52, 0xFE52, 0xFE55, 0xFE55, 0xFF07, 0xFF07, 0xFF0E,
                        0xFF0E, 0xFF1A, 0xFF1A);
    }

    // (Other_Default_Ignorable_Code_Point + Cf + Variation_Selector) - White_Space - FFF9..FFFB
    // - the Egyptian hieroglyph format controls - Prepended_Concatenation_Mark
    private static boolean isDefaultIgnorable(int c) {
        boolean candidate =
                Character.getType(c) == Character.FORMAT
                        || isVariationSelector(c)
                        || in(
                                c, 0x034F, 0x034F, 0x115F, 0x1160, 0x17B4, 0x17B5, 0x2065, 0x2065,
                                0x3164, 0x3164, 0xFFA0, 0xFFA0, 0xFFF0, 0xFFF8, 0xE0000, 0xE0000,
                                0xE0002, 0xE001F, 0xE0080, 0xE00FF, 0xE01F0, 0xE0FFF);
        return candidate
                && !Character.isWhitespace(c)
                && !in(
                        c, 0xFFF9, 0xFFFB, 0x13430, 0x1343F, 0x0600, 0x0605, 0x06DD, 0x06DD, 0x070F,
                        0x070F, 0x0890, 0x0891, 0x08E2, 0x08E2, 0x110BD, 0x110BD, 0x110CD, 0x110CD);
    }

    private static boolean isUnifiedIdeograph(int c) {
        if (in(
                c, 0xFA0E, 0xFA0F, 0xFA11, 0xFA11, 0xFA13, 0xFA14, 0xFA1F, 0xFA1F, 0xFA21, 0xFA21,
                0xFA23, 0xFA24, 0xFA27, 0xFA29)) {
            return true;
        }
        if (!Character.isIdeographic(c)) {
            return false;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block != null && block.toString().startsWith("CJK_UNIFIED_IDEOGRAPHS");
    }

    // The Changes_When_* properties are defined on the NFD form of the character
    private static String nfd(int c) {
        return Normalizer.normalize(Character.toString(c), Normalizer.Form.NFD);
    }

    private static boolean changesWhenLowercased(int c) {
        String s = nfd(c);
        return !s.equals(s.toLowerCase(Locale.ROOT));
    }

    private static boolean changesWhenUppercased(int c) {
        String s = nfd(c);
        return !s.equals(s.toUpperCase(Locale.ROOT));
    }

    // Titlecasing maps the first character to titlecase and lowercases the rest. The JDK has no
    // full titlecase mapping: a character whose full uppercase mapping has several characters
    // (SpecialCasing.txt) changes, unless it is a titlecase letter.
    private static boolean changesWhenTitlecased(int c) {
        String s = nfd(c);
        int first = s.codePointAt(0);
        String rest = s.substring(Character.charCount(first));
        if (!rest.equals(rest.toLowerCase(Locale.ROOT)) || Character.toTitleCase(first) != first) {
            return true;
        }
        String upper = Character.toString(first).toUpperCase(Locale.ROOT);
        return !Character.isTitleCase(first) && upper.codePointCount(0, upper.length()) > 1;
    }

    private static boolean changesWhenCasefolded(int c) {
        String s = nfd(c);
        return !s.equals(caseFold(s));
    }

    // Full case folding (CaseFolding.txt, status C and F), which the JDK does not have: the
    // lowercase of the uppercase, except for U+0131, which does not fold, and Cherokee, which folds
    // to uppercase.
    private static String caseFold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints()
                .forEach(
                        c -> {
                            if (c == 0x0131 || in(c, 0x13A0, 0x13F5)) {
                                b.appendCodePoint(c);
                            } else if (in(c, 0x13F8, 0x13FD, 0xAB70, 0xABBF)) {
                                b.appendCodePoint(Character.toUpperCase(c));
                            } else {
                                b.append(
                                        Character.toString(c)
                                                .toUpperCase(Locale.ROOT)
                                                .toLowerCase(Locale.ROOT));
                            }
                        });
        return b.toString();
    }

    // NFKC_Casefold: case folding, removal of the default ignorable code points and NFKC, until
    // the result is stable
    private static boolean changesWhenNfkcCasefolded(int c) {
        String s = Character.toString(c);
        String current = s;
        String previous;
        do {
            previous = current;
            StringBuilder b = new StringBuilder();
            caseFold(Normalizer.normalize(current, Normalizer.Form.NFD))
                    .codePoints()
                    .filter(d -> !isDefaultIgnorable(d))
                    .forEach(b::appendCodePoint);
            current = Normalizer.normalize(b, Normalizer.Form.NFKC);
        } while (!current.equals(previous));
        return !s.equals(current);
    }

    /**
     * Encodes a property name and value into a single int. The property name is in the high 16
     * bits, the value in the low 16 bits.
     *
     * @param property Property name constant
     * @param value Property value constant
     * @return Encoded int
     */
    private static int encodeProperty(byte property, byte value) {
        return ((property & 0xFF) << 8) | (value & 0xFF);
    }

    private static final Character.UnicodeScript[] UnicodeScriptValues =
            Character.UnicodeScript.values();

    /**
     * Tests if a code point has a specific Unicode property (direct check without case handling).
     */
    public static boolean hasProperty(int property, int codePoint) {
        byte propByte = (byte) ((property >> 8) & 0xFF);
        int valueByte = (property & 0xFF);
        return hasPropertyDirect(propByte, valueByte, codePoint);
    }

    /** Direct property check without any case handling. */
    private static boolean hasPropertyDirect(byte propByte, int valueByte, int codePoint) {
        switch (propByte) {
            case ES_GENERAL_CATEGORY:
                return (ES_GENERAL_CATEGORY_MASKS[valueByte] & (1 << Character.getType(codePoint)))
                        != 0;
            case ES_BINARY:
                return Binary.VALUES[valueByte].predicate.test(codePoint);
            case ALPHABETIC:
                return Character.isAlphabetic(codePoint) == (valueByte == TRUE);

            case ASCII:
                return (codePoint <= 0x7F) == (valueByte == TRUE);

            case CASE_IGNORABLE:
                // Java doesn't have a direct method for this
                // This is an approximation
                return (Character.getType(codePoint) == Character.MODIFIER_SYMBOL
                                || Character.getType(codePoint) == Character.MODIFIER_LETTER
                                || Character.getType(codePoint) == Character.NON_SPACING_MARK)
                        == (valueByte == TRUE);

            case GENERAL_CATEGORY:
                int javaCategory = Character.getType(codePoint);
                return checkGeneralCategory(valueByte, javaCategory);
            case ASCII_HEX_DIGIT:
                return isHexDigit(codePoint) == (valueByte == TRUE);
            case HEX_DIGIT:
                return (Character.digit(codePoint, 16) != -1) == (valueByte == TRUE);
            case ID_CONTINUE:
                return Character.isUnicodeIdentifierPart(codePoint) == (valueByte == TRUE);

            case ID_START:
                return Character.isUnicodeIdentifierStart(codePoint) == (valueByte == TRUE);

            case LOWERCASE:
                return Character.isLowerCase(codePoint) == (valueByte == TRUE);

            case UPPERCASE:
                return Character.isUpperCase(codePoint) == (valueByte == TRUE);

            case WHITE_SPACE:
                {
                    // Note: This only a good approximation of the Unicode white space property
                    return (valueByte == TRUE)
                            == (Character.isSpaceChar(codePoint)
                                    || Character.isWhitespace(codePoint));
                }
            case SCRIPT:
                return Character.UnicodeScript.of(codePoint) == UnicodeScriptValues[valueByte];
            default:
                return false;
        }
    }

    /** Maps our property value bytes to Java's Character.getType() values. */
    private static boolean checkGeneralCategory(int propertyValueByte, int javaCategory) {
        switch (propertyValueByte) {
            case LETTER:
                return javaCategory == Character.UPPERCASE_LETTER
                        || javaCategory == Character.LOWERCASE_LETTER
                        || javaCategory == Character.TITLECASE_LETTER
                        || javaCategory == Character.MODIFIER_LETTER
                        || javaCategory == Character.OTHER_LETTER;
            case UPPERCASE_LETTER:
                return javaCategory == Character.UPPERCASE_LETTER;
            case LOWERCASE_LETTER:
                return javaCategory == Character.LOWERCASE_LETTER;
            case TITLECASE_LETTER:
                return javaCategory == Character.TITLECASE_LETTER;
            case MODIFIER_LETTER:
                return javaCategory == Character.MODIFIER_LETTER;
            case OTHER_LETTER:
                return javaCategory == Character.OTHER_LETTER;
            case MARK:
                return javaCategory == Character.NON_SPACING_MARK
                        || javaCategory == Character.ENCLOSING_MARK
                        || javaCategory == Character.COMBINING_SPACING_MARK;
            case NONSPACING_MARK:
                return javaCategory == Character.NON_SPACING_MARK;
            case ENCLOSING_MARK:
                return javaCategory == Character.ENCLOSING_MARK;
            case SPACING_MARK:
                return javaCategory == Character.COMBINING_SPACING_MARK;
            case NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER
                        || javaCategory == Character.LETTER_NUMBER
                        || javaCategory == Character.OTHER_NUMBER;
            case DECIMAL_NUMBER:
                return javaCategory == Character.DECIMAL_DIGIT_NUMBER;
            case LETTER_NUMBER:
                return javaCategory == Character.LETTER_NUMBER;
            case OTHER_NUMBER:
                return javaCategory == Character.OTHER_NUMBER;

            case SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR
                        || javaCategory == Character.LINE_SEPARATOR
                        || javaCategory == Character.PARAGRAPH_SEPARATOR;
            case SPACE_SEPARATOR:
                return javaCategory == Character.SPACE_SEPARATOR;
            case LINE_SEPARATOR:
                return javaCategory == Character.LINE_SEPARATOR;
            case PARAGRAPH_SEPARATOR:
                return javaCategory == Character.PARAGRAPH_SEPARATOR;

            case OTHER:
                return javaCategory == Character.OTHER_LETTER
                        || javaCategory == Character.OTHER_NUMBER
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.OTHER_SYMBOL;
            case CONTROL:
                return javaCategory == Character.CONTROL;
            case FORMAT:
                return javaCategory == Character.FORMAT;
            case SURROGATE:
                return javaCategory == Character.SURROGATE;
            case PRIVATE_USE:
                return javaCategory == Character.PRIVATE_USE;

            case PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION
                        || javaCategory == Character.DASH_PUNCTUATION
                        || javaCategory == Character.START_PUNCTUATION
                        || javaCategory == Character.END_PUNCTUATION
                        || javaCategory == Character.OTHER_PUNCTUATION
                        || javaCategory == Character.INITIAL_QUOTE_PUNCTUATION
                        || javaCategory == Character.FINAL_QUOTE_PUNCTUATION;
            case DASH_PUNCTUATION:
                return javaCategory == Character.DASH_PUNCTUATION;
            case OPEN_PUNCTUATION:
                return javaCategory == Character.START_PUNCTUATION;
            case CLOSE_PUNCTUATION:
                return javaCategory == Character.END_PUNCTUATION;
            case CONNECTOR_PUNCTUATION:
                return javaCategory == Character.CONNECTOR_PUNCTUATION;
            case OTHER_PUNCTUATION:
                return javaCategory == Character.OTHER_PUNCTUATION;
            case INITIAL_PUNCTUATION:
                return javaCategory == Character.INITIAL_QUOTE_PUNCTUATION;
            case FINAL_PUNCTUATION:
                return javaCategory == Character.FINAL_QUOTE_PUNCTUATION;

            case SYMBOL:
                return javaCategory == Character.MATH_SYMBOL
                        || javaCategory == Character.CURRENCY_SYMBOL
                        || javaCategory == Character.MODIFIER_SYMBOL
                        || javaCategory == Character.OTHER_SYMBOL;
            case MATH_SYMBOL:
                return javaCategory == Character.MATH_SYMBOL;
            case CURRENCY_SYMBOL:
                return javaCategory == Character.CURRENCY_SYMBOL;
            case MODIFIER_SYMBOL:
                return javaCategory == Character.MODIFIER_SYMBOL;
            case OTHER_SYMBOL:
                return javaCategory == Character.OTHER_SYMBOL;
            case UNASSIGNED:
                return javaCategory == Character.UNASSIGNED;

            default:
                return false;
        }
    }

    /** Checks if a code point is a hex digit. */
    private static boolean isHexDigit(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || (codePoint >= 'a' && codePoint <= 'f')
                || (codePoint >= 'A' && codePoint <= 'F');
    }
}
