package art.arcane.optics.internal.shape;

import java.util.ArrayList;
import java.util.List;

public final class ShapeTokens {
    private static final String SYMBOLS = "(),=:;+&-@";

    private ShapeTokens() {
    }

    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<Token>();
        int index = 0;
        int length = text.length();
        while (index < length) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character)) {
                index++;
                continue;
            }
            if (isLetter(character)) {
                int start = index;
                while (index < length && isLetter(text.charAt(index))) {
                    index++;
                }
                tokens.add(new Token(Kind.NAME, text.substring(start, index), 0.0D, start));
                continue;
            }
            if (isDigit(character) || (character == '.' && index + 1 < length && isDigit(text.charAt(index + 1)))) {
                int start = index;
                index = numberEnd(text, index);
                String literal = text.substring(start, index);
                double value;
                try {
                    value = Double.parseDouble(literal);
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Malformed number '" + literal + "' at position " + start, invalid);
                }
                tokens.add(new Token(Kind.NUMBER, literal, value, start));
                continue;
            }
            if (SYMBOLS.indexOf(character) >= 0) {
                tokens.add(new Token(Kind.SYMBOL, String.valueOf(character), 0.0D, index));
                index++;
                continue;
            }
            throw new IllegalArgumentException("Unexpected character '" + character + "' at position " + index);
        }
        tokens.add(new Token(Kind.END, "", 0.0D, length));
        return tokens;
    }

    private static int numberEnd(String text, int start) {
        int index = start;
        int length = text.length();
        while (index < length && isDigit(text.charAt(index))) {
            index++;
        }
        if (index < length && text.charAt(index) == '.') {
            index++;
            while (index < length && isDigit(text.charAt(index))) {
                index++;
            }
        }
        if (index < length && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            int exponent = index + 1;
            if (exponent < length && (text.charAt(exponent) == '+' || text.charAt(exponent) == '-')) {
                exponent++;
            }
            if (exponent < length && isDigit(text.charAt(exponent))) {
                index = exponent;
                while (index < length && isDigit(text.charAt(index))) {
                    index++;
                }
            }
        }
        return index;
    }

    private static boolean isLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || character == '_';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    public enum Kind {
        NAME,
        NUMBER,
        SYMBOL,
        END
    }

    public record Token(Kind kind, String text, double number, int position) {
        public boolean is(char symbol) {
            return kind == Kind.SYMBOL && text.charAt(0) == symbol;
        }
    }
}
