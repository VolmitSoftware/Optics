package art.arcane.optics.internal.shape;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import art.arcane.optics.shape.Difference;
import art.arcane.optics.shape.Ellipse;
import art.arcane.optics.shape.Feather;
import art.arcane.optics.shape.FitMode;
import art.arcane.optics.shape.Flower;
import art.arcane.optics.shape.Heart;
import art.arcane.optics.shape.Intersection;
import art.arcane.optics.shape.Path;
import art.arcane.optics.shape.PlaneTransform;
import art.arcane.optics.shape.Polygon;
import art.arcane.optics.shape.Rectangle;
import art.arcane.optics.shape.RegularPolygon;
import art.arcane.optics.shape.Ring;
import art.arcane.optics.shape.RoundedRectangle;
import art.arcane.optics.shape.Shape;
import art.arcane.optics.shape.Spline;
import art.arcane.optics.shape.Star;
import art.arcane.optics.shape.Transformed;
import art.arcane.optics.shape.Union;

public final class ShapeGrammar {
    private static final int DEFAULT_SPLINE_SEGMENTS = 8;
    private static final int MIN_DIGITS = 6;
    private static final int MAX_DIGITS = 9;
    private static final int MAX_NESTING = 64;

    private final String text;
    private final List<ShapeTokens.Token> tokens;
    private final boolean allowFit;
    private int position;
    private int nesting;
    private FitMode fit;

    private ShapeGrammar(String text, boolean allowFit) {
        this.text = text;
        this.tokens = ShapeTokens.tokenize(text);
        this.allowFit = allowFit;
    }

    public static Parsed parse(String text, boolean allowFit) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Shape text is empty at position 0");
        }
        ShapeGrammar grammar = new ShapeGrammar(text, allowFit);
        Shape shape = grammar.shape();
        ShapeTokens.Token end = grammar.peek();
        if (end.kind() != ShapeTokens.Kind.END) {
            throw grammar.error("Unexpected '" + end.text() + "'", end);
        }
        return new Parsed(shape, grammar.fit);
    }

    public static PlaneTransform modifiers(String text) {
        ShapeGrammar grammar = new ShapeGrammar(text, false);
        PlaneTransform transform = grammar.modifierChain();
        ShapeTokens.Token end = grammar.peek();
        if (end.kind() != ShapeTokens.Kind.END) {
            throw grammar.error("Unexpected '" + end.text() + "'", end);
        }
        return transform == null ? PlaneTransform.IDENTITY : transform;
    }

    public static String format(Shape shape) {
        StringBuilder out = new StringBuilder();
        write(out, shape);
        return out.toString();
    }

    public static String number(double value) {
        float target = (float) value;
        for (int digits = MIN_DIGITS; digits <= MAX_DIGITS; digits++) {
            String candidate = new BigDecimal(value).round(new MathContext(digits)).stripTrailingZeros().toPlainString();
            if (Float.floatToIntBits((float) Double.parseDouble(candidate)) == Float.floatToIntBits(target)) {
                return candidate;
            }
        }
        return new BigDecimal(Float.toString(target)).stripTrailingZeros().toPlainString();
    }

    private Shape shape() {
        if (++nesting > MAX_NESTING) {
            throw error("Shape nests too deeply", peek());
        }
        Shape left = term();
        while (true) {
            ShapeTokens.Token operator = peek();
            if (operator.is('+')) {
                position++;
                left = new Union(left, term());
            } else if (operator.is('&')) {
                position++;
                left = new Intersection(left, term());
            } else if (operator.is('-')) {
                position++;
                left = new Difference(left, term());
            } else {
                nesting--;
                return left;
            }
        }
    }

    private Shape term() {
        ShapeTokens.Token start = peek();
        Shape base;
        if (start.is('(')) {
            position++;
            base = shape();
            expect(')');
        } else if (start.kind() == ShapeTokens.Kind.NAME) {
            base = call();
        } else {
            throw error("Expected a shape", start);
        }
        PlaneTransform transform = modifierChain();
        return transform == null ? base : new Transformed(base, transform);
    }

    private PlaneTransform modifierChain() {
        PlaneTransform transform = null;
        while (peek().is('@')) {
            ShapeTokens.Token at = next();
            ShapeTokens.Token name = next();
            if (name.kind() != ShapeTokens.Kind.NAME) {
                throw error("Expected a modifier name", name);
            }
            String key = name.text().toLowerCase(Locale.ROOT);
            if (key.equals("fit")) {
                readFit(at);
                continue;
            }
            PlaneTransform step = switch (key) {
                case "rotate" -> PlaneTransform.rotation(single(name));
                case "scale" -> scale(name);
                case "offset" -> offset(name);
                case "flipu" -> PlaneTransform.flipU();
                case "flipv" -> PlaneTransform.flipV();
                case "transform" -> matrix(name);
                default -> throw error("Unknown modifier '" + name.text() + "'", name);
            };
            transform = transform == null ? step : step.compose(transform);
        }
        return transform;
    }

    private void readFit(ShapeTokens.Token at) {
        if (!allowFit) {
            throw error("The fit modifier belongs to a shape descriptor, not a shape", at);
        }
        if (fit != null || nesting != 1) {
            throw error("The fit modifier may appear once, last", at);
        }
        expect('(');
        ShapeTokens.Token mode = next();
        if (mode.kind() != ShapeTokens.Kind.NAME) {
            throw error("Expected contain, cover or stretch", mode);
        }
        fit = switch (mode.text().toLowerCase(Locale.ROOT)) {
            case "contain" -> FitMode.CONTAIN;
            case "cover" -> FitMode.COVER;
            case "stretch" -> FitMode.STRETCH;
            default -> throw error("Expected contain, cover or stretch", mode);
        };
        expect(')');
        if (peek().kind() != ShapeTokens.Kind.END) {
            throw error("The fit modifier may appear once, last", at);
        }
    }

    private double single(ShapeTokens.Token name) {
        expect('(');
        double value = number();
        expect(')');
        return value;
    }

    private PlaneTransform scale(ShapeTokens.Token name) {
        expect('(');
        double factorU = number();
        double factorV = peek().is(',') ? secondNumber() : factorU;
        expect(')');
        return guard(() -> PlaneTransform.scale(factorU, factorV), name);
    }

    private double secondNumber() {
        position++;
        return number();
    }

    private PlaneTransform offset(ShapeTokens.Token name) {
        expect('(');
        double du = number();
        expect(',');
        double dv = number();
        expect(')');
        return guard(() -> PlaneTransform.translation(du, dv), name);
    }

    private PlaneTransform matrix(ShapeTokens.Token name) {
        expect('(');
        double[] values = new double[6];
        for (int index = 0; index < 6; index++) {
            if (index > 0) {
                expect(',');
            }
            values[index] = number();
        }
        expect(')');
        return guard(() -> new PlaneTransform(values[0], values[1], values[2], values[3], values[4], values[5]), name);
    }

    private Shape call() {
        ShapeTokens.Token name = next();
        String key = name.text().toLowerCase(Locale.ROOT);
        if (key.equals("path")) {
            return guard(() -> pathCall(), name);
        }
        Arguments arguments = arguments();
        return guard(() -> build(key, name, arguments), name);
    }

    private Shape build(String key, ShapeTokens.Token name, Arguments arguments) {
        return switch (key) {
            case "full" -> {
                arguments.bind(this, List.of());
                yield new Rectangle(2.0D, 2.0D);
            }
            case "rectangle" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("width", "height"));
                yield new Rectangle(scalar(bound, "width", 2.0D), scalar(bound, "height", 2.0D));
            }
            case "rounded" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("width", "height", "radius"));
                yield new RoundedRectangle(scalar(bound, "width", 2.0D), scalar(bound, "height", 2.0D), scalar(bound, "radius", 0.25D));
            }
            case "circle" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("radius"));
                yield Ellipse.circle(scalar(bound, "radius", 1.0D));
            }
            case "ellipse" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("radiusu", "radiusv"));
                yield new Ellipse(scalar(bound, "radiusu", 1.0D), scalar(bound, "radiusv", 1.0D));
            }
            case "polygon" -> polygon(arguments);
            case "star" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("points", "outer", "inner", "rotate"));
                yield new Star(integer(bound, "points", 5), scalar(bound, "outer", 1.0D), scalar(bound, "inner", 0.45D),
                    scalar(bound, "rotate", 0.0D));
            }
            case "flower" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("petals", "radius", "depth", "rotate"));
                yield new Flower(integer(bound, "petals", 5), scalar(bound, "radius", 1.0D), scalar(bound, "depth", 0.6D),
                    scalar(bound, "rotate", 0.0D));
            }
            case "heart" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("size", "rotate"));
                yield new Heart(scalar(bound, "size", 1.0D), scalar(bound, "rotate", 0.0D));
            }
            case "feather" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("length", "width", "curve", "rotate"));
                yield new Feather(scalar(bound, "length", 2.0D), scalar(bound, "width", 1.0D), scalar(bound, "curve", 0.25D),
                    scalar(bound, "rotate", 0.0D));
            }
            case "ring" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("outer", "inner"));
                yield new Ring(scalar(bound, "outer", 1.0D), scalar(bound, "inner", 0.6D));
            }
            case "spline" -> {
                Map<String, Value> bound = arguments.bind(this, List.of("points", "segments"));
                Value points = bound.get("points");
                if (points == null) {
                    throw error("spline needs points", name);
                }
                yield new Spline(points.points(this), integer(bound, "segments", DEFAULT_SPLINE_SEGMENTS));
            }
            default -> throw error("Unknown shape '" + name.text() + "'", name);
        };
    }

    private Shape polygon(Arguments arguments) {
        Value first = arguments.positional.isEmpty() ? null : arguments.positional.getFirst();
        boolean custom = arguments.keyed.containsKey("points") || (first != null && first.isPoints());
        if (custom) {
            Map<String, Value> bound = arguments.bind(this, List.of("points"));
            return new Polygon(bound.get("points").points(this));
        }
        Map<String, Value> bound = arguments.bind(this, List.of("sides", "radius", "rotate"));
        return new RegularPolygon(integer(bound, "sides", 6), scalar(bound, "radius", 1.0D), scalar(bound, "rotate", 0.0D));
    }

    private Shape pathCall() {
        expect('(');
        ShapeTokens.Token first = peek();
        if (first.kind() == ShapeTokens.Kind.NAME && first.text().equalsIgnoreCase("d") && tokens.get(position + 1).is('=')) {
            position += 2;
        }
        Path.Builder builder = Path.builder();
        boolean any = false;
        while (peek().kind() == ShapeTokens.Kind.NAME) {
            ShapeTokens.Token commands = next();
            String letters = commands.text().toUpperCase(Locale.ROOT);
            for (int index = 0; index < letters.length(); index++) {
                char command = letters.charAt(index);
                boolean last = index == letters.length() - 1;
                any = true;
                switch (command) {
                    case 'Z' -> builder.close();
                    case 'M', 'L', 'Q', 'C' -> {
                        if (!last) {
                            throw error("Path command " + command + " needs points", commands);
                        }
                        double[] points = pathPoints(command == 'Q' ? 2 : command == 'C' ? 3 : 1, commands);
                        switch (command) {
                            case 'M' -> builder.moveTo(points[0], points[1]);
                            case 'L' -> builder.lineTo(points[0], points[1]);
                            case 'Q' -> builder.quadTo(points[0], points[1], points[2], points[3]);
                            default -> builder.cubicTo(points[0], points[1], points[2], points[3], points[4], points[5]);
                        }
                    }
                    default -> throw error("Unknown path command '" + command + "'", commands);
                }
            }
        }
        if (!any) {
            throw error("path needs d=<commands>", peek());
        }
        expect(')');
        return builder.build();
    }

    private double[] pathPoints(int count, ShapeTokens.Token command) {
        double[] points = new double[count << 1];
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                expect(';');
            }
            points[index << 1] = number();
            expect(':');
            points[(index << 1) + 1] = number();
        }
        return points;
    }

    private Arguments arguments() {
        Arguments arguments = new Arguments();
        if (!peek().is('(')) {
            return arguments;
        }
        position++;
        if (peek().is(')')) {
            position++;
            return arguments;
        }
        while (true) {
            ShapeTokens.Token start = peek();
            if (start.kind() == ShapeTokens.Kind.NAME && tokens.get(position + 1).is('=')) {
                position += 2;
                String key = start.text().toLowerCase(Locale.ROOT);
                if (arguments.keyed.containsKey(key)) {
                    throw error("Duplicate argument '" + start.text() + "'", start);
                }
                arguments.keyed.put(key, value());
                arguments.keyTokens.put(key, start);
            } else {
                if (!arguments.keyed.isEmpty()) {
                    throw error("Positional arguments must come before keyed arguments", start);
                }
                arguments.positional.add(value());
                arguments.positionTokens.add(start);
            }
            ShapeTokens.Token separator = next();
            if (separator.is(')')) {
                return arguments;
            }
            if (!separator.is(',')) {
                throw error("Expected ',' or ')'", separator);
            }
        }
    }

    private Value value() {
        ShapeTokens.Token start = peek();
        double first = number();
        if (!peek().is(':')) {
            return new Value(first, null, start);
        }
        List<Double> points = new ArrayList<Double>();
        position++;
        points.add(first);
        points.add(number());
        while (peek().is(';')) {
            position++;
            points.add(number());
            expect(':');
            points.add(number());
        }
        double[] array = new double[points.size()];
        for (int index = 0; index < array.length; index++) {
            array[index] = points.get(index);
        }
        return new Value(0.0D, array, start);
    }

    private double number() {
        ShapeTokens.Token token = next();
        boolean negative = false;
        if (token.is('-')) {
            negative = true;
            token = next();
        }
        if (token.kind() != ShapeTokens.Kind.NUMBER) {
            throw error("Expected a number", token);
        }
        return negative ? -token.number() : token.number();
    }

    private double scalar(Map<String, Value> bound, String key, double fallback) {
        Value value = bound.get(key);
        return value == null ? fallback : value.scalar(this);
    }

    private int integer(Map<String, Value> bound, String key, int fallback) {
        Value value = bound.get(key);
        if (value == null) {
            return fallback;
        }
        double scalar = value.scalar(this);
        if (scalar != Math.rint(scalar) || Math.abs(scalar) > 1.0E6D) {
            throw error("Argument '" + key + "' must be a whole number", value.token);
        }
        return (int) scalar;
    }

    private void expect(char symbol) {
        ShapeTokens.Token token = next();
        if (!token.is(symbol)) {
            throw error("Expected '" + symbol + "'", token);
        }
    }

    private ShapeTokens.Token peek() {
        return tokens.get(position);
    }

    private ShapeTokens.Token next() {
        ShapeTokens.Token token = tokens.get(position);
        if (token.kind() != ShapeTokens.Kind.END) {
            position++;
        }
        return token;
    }

    private IllegalArgumentException error(String message, ShapeTokens.Token token) {
        return new IllegalArgumentException(message + " at position " + token.position() + " in \"" + text + "\"");
    }

    private <T> T guard(Construction<T> construction, ShapeTokens.Token token) {
        try {
            return construction.build();
        } catch (IllegalArgumentException invalid) {
            if (invalid.getMessage() != null && invalid.getMessage().contains(" at position ")) {
                throw invalid;
            }
            throw new IllegalArgumentException(invalid.getMessage() + " at position " + token.position() + " in \"" + text + "\"", invalid);
        } catch (IllegalStateException invalid) {
            throw new IllegalArgumentException(invalid.getMessage() + " at position " + token.position() + " in \"" + text + "\"", invalid);
        }
    }

    private static void write(StringBuilder out, Shape shape) {
        switch (shape) {
            case Rectangle rectangle -> {
                if (rectangle.width() == 2.0D && rectangle.height() == 2.0D) {
                    out.append("full");
                } else {
                    out.append("rectangle(width=").append(number(rectangle.width())).append(",height=").append(number(rectangle.height()))
                        .append(')');
                }
            }
            case RoundedRectangle rounded -> out.append("rounded(width=").append(number(rounded.width())).append(",height=")
                .append(number(rounded.height())).append(",radius=").append(number(rounded.radius())).append(')');
            case Ellipse ellipse -> {
                if (ellipse.radiusU() == ellipse.radiusV()) {
                    out.append("circle(radius=").append(number(ellipse.radiusU())).append(')');
                } else {
                    out.append("ellipse(radiusU=").append(number(ellipse.radiusU())).append(",radiusV=").append(number(ellipse.radiusV()))
                        .append(')');
                }
            }
            case RegularPolygon polygon -> out.append("polygon(sides=").append(polygon.sides()).append(",radius=")
                .append(number(polygon.radius())).append(",rotate=").append(number(polygon.rotationDegrees())).append(')');
            case Star star -> out.append("star(points=").append(star.points()).append(",outer=").append(number(star.outerRadius()))
                .append(",inner=").append(number(star.innerRadius())).append(",rotate=").append(number(star.rotationDegrees())).append(')');
            case Flower flower -> out.append("flower(petals=").append(flower.petals()).append(",radius=").append(number(flower.radius()))
                .append(",depth=").append(number(flower.petalDepth())).append(",rotate=").append(number(flower.rotationDegrees())).append(')');
            case Heart heart -> out.append("heart(size=").append(number(heart.size())).append(",rotate=")
                .append(number(heart.rotationDegrees())).append(')');
            case Feather feather -> out.append("feather(length=").append(number(feather.length())).append(",width=")
                .append(number(feather.width())).append(",curve=").append(number(feather.curve())).append(",rotate=")
                .append(number(feather.rotationDegrees())).append(')');
            case Ring ring -> out.append("ring(outer=").append(number(ring.outerRadius())).append(",inner=").append(number(ring.innerRadius()))
                .append(')');
            case Polygon polygon -> {
                out.append("polygon(points=");
                points(out, polygon.points());
                out.append(')');
            }
            case Spline spline -> {
                out.append("spline(points=");
                points(out, spline.controlPoints());
                out.append(",segments=").append(spline.segmentsPerSpan()).append(')');
            }
            case Path path -> path(out, path);
            case Union union -> binary(out, union.left(), '+', union.right());
            case Intersection intersection -> binary(out, intersection.left(), '&', intersection.right());
            case Difference difference -> binary(out, difference.left(), '-', difference.right());
            case Transformed transformed -> {
                Shape child = transformed.shape();
                boolean wrap = child instanceof Transformed || child instanceof Union || child instanceof Intersection
                    || child instanceof Difference;
                if (wrap) {
                    out.append('(');
                }
                write(out, child);
                if (wrap) {
                    out.append(')');
                }
                out.append(modifierText(transformed.transform()));
            }
            default -> throw new IllegalArgumentException("Unsupported shape type " + shape.getClass().getSimpleName());
        }
    }

    private static void binary(StringBuilder out, Shape left, char operator, Shape right) {
        write(out, left);
        out.append(operator);
        boolean wrap = right instanceof Union || right instanceof Intersection || right instanceof Difference;
        if (wrap) {
            out.append('(');
        }
        write(out, right);
        if (wrap) {
            out.append(')');
        }
    }

    private static void points(StringBuilder out, double[] points) {
        for (int index = 0; index < points.length; index += 2) {
            if (index > 0) {
                out.append(';');
            }
            out.append(number(points[index])).append(':').append(number(points[index + 1]));
        }
    }

    private static void path(StringBuilder out, Path path) {
        out.append("path(d=");
        boolean first = true;
        for (Path.Segment segment : path.segments()) {
            if (!first) {
                out.append(' ');
            }
            first = false;
            switch (segment) {
                case Path.Move move -> out.append("M ").append(number(move.u())).append(':').append(number(move.v()));
                case Path.Line line -> out.append("L ").append(number(line.u())).append(':').append(number(line.v()));
                case Path.Quad quad -> out.append("Q ").append(number(quad.cu())).append(':').append(number(quad.cv())).append(';')
                    .append(number(quad.u())).append(':').append(number(quad.v()));
                case Path.Cubic cubic -> out.append("C ").append(number(cubic.c1u())).append(':').append(number(cubic.c1v())).append(';')
                    .append(number(cubic.c2u())).append(':').append(number(cubic.c2v())).append(';')
                    .append(number(cubic.u())).append(':').append(number(cubic.v()));
                case Path.Close _ -> out.append('Z');
            }
        }
        out.append(')');
    }

    private static String modifierText(PlaneTransform transform) {
        String offset = transform.tu() == 0.0D && transform.tv() == 0.0D ? ""
            : "@offset(" + number(transform.tu()) + "," + number(transform.tv()) + ")";
        for (String linear : linearCandidates(transform)) {
            String candidate = linear + offset;
            if (candidate.isEmpty()) {
                candidate = "@offset(0,0)";
            }
            if (sameSingle(modifiers(candidate), transform)) {
                return candidate;
            }
        }
        return "@transform(" + number(transform.a()) + "," + number(transform.b()) + "," + number(transform.c()) + ","
            + number(transform.d()) + "," + number(transform.tu()) + "," + number(transform.tv()) + ")";
    }

    private static List<String> linearCandidates(PlaneTransform transform) {
        double a = transform.a();
        double b = transform.b();
        double c = transform.c();
        double d = transform.d();
        List<String> candidates = new ArrayList<String>(7);
        candidates.add("");
        candidates.add("@flipU");
        candidates.add("@flipV");
        double rotateFirst = StrictMath.atan2(-b, a);
        double scaleFirstU = Math.sqrt(a * a + b * b);
        double scaleFirstV = c * StrictMath.sin(rotateFirst) + d * StrictMath.cos(rotateFirst);
        double rotateLast = StrictMath.atan2(c, a);
        double scaleLastU = Math.sqrt(a * a + c * c);
        double scaleLastV = -b * StrictMath.sin(rotateLast) + d * StrictMath.cos(rotateLast);
        for (int digits = MIN_DIGITS; digits <= MAX_DIGITS; digits += MAX_DIGITS - MIN_DIGITS) {
            candidates.add(rotateText(rotateFirst, digits) + scaleText(scaleFirstU, scaleFirstV, digits));
            candidates.add(scaleText(scaleLastU, scaleLastV, digits) + rotateText(rotateLast, digits));
        }
        return candidates;
    }

    private static String rotateText(double radians, int digits) {
        String degrees = plain(Math.toDegrees(radians), digits);
        return degrees.equals("0") ? "" : "@rotate(" + degrees + ")";
    }

    private static String scaleText(double factorU, double factorV, int digits) {
        String u = plain(factorU, digits);
        String v = plain(factorV, digits);
        if (u.equals("1") && v.equals("1")) {
            return "";
        }
        return u.equals(v) ? "@scale(" + u + ")" : "@scale(" + u + "," + v + ")";
    }

    private static String plain(double value, int digits) {
        return new BigDecimal(value).round(new MathContext(digits)).stripTrailingZeros().toPlainString();
    }

    private static boolean sameSingle(PlaneTransform first, PlaneTransform second) {
        return sameSingle(first.a(), second.a()) && sameSingle(first.b(), second.b()) && sameSingle(first.c(), second.c())
            && sameSingle(first.d(), second.d()) && sameSingle(first.tu(), second.tu()) && sameSingle(first.tv(), second.tv());
    }

    private static boolean sameSingle(double first, double second) {
        return Float.floatToIntBits((float) first) == Float.floatToIntBits((float) second);
    }

    public record Parsed(Shape shape, FitMode fit) {
    }

    private interface Construction<T> {
        T build();
    }

    private static final class Arguments {
        private final List<Value> positional = new ArrayList<Value>();
        private final List<ShapeTokens.Token> positionTokens = new ArrayList<ShapeTokens.Token>();
        private final Map<String, Value> keyed = new HashMap<String, Value>();
        private final Map<String, ShapeTokens.Token> keyTokens = new HashMap<String, ShapeTokens.Token>();

        private Map<String, Value> bind(ShapeGrammar grammar, List<String> names) {
            if (positional.size() > names.size()) {
                throw grammar.error("Too many arguments", positionTokens.get(names.size()));
            }
            Map<String, Value> bound = new HashMap<String, Value>();
            for (int index = 0; index < positional.size(); index++) {
                bound.put(names.get(index), positional.get(index));
            }
            for (Map.Entry<String, Value> entry : keyed.entrySet()) {
                if (!names.contains(entry.getKey())) {
                    throw grammar.error("Unknown argument '" + entry.getKey() + "'", keyTokens.get(entry.getKey()));
                }
                if (bound.containsKey(entry.getKey())) {
                    throw grammar.error("Argument '" + entry.getKey() + "' given twice", keyTokens.get(entry.getKey()));
                }
                bound.put(entry.getKey(), entry.getValue());
            }
            return bound;
        }
    }

    private record Value(double scalar, double[] points, ShapeTokens.Token token) {
        private boolean isPoints() {
            return points != null;
        }

        private double scalar(ShapeGrammar grammar) {
            if (points != null) {
                throw grammar.error("Expected a number", token);
            }
            return scalar;
        }

        private double[] points(ShapeGrammar grammar) {
            if (points == null) {
                throw grammar.error("Expected a point list u:v;u:v", token);
            }
            return points;
        }
    }
}
