package net.neoforged.jst.unpick;

import daomephsta.unpick.constantmappers.datadriven.tree.DataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that is used to compute mathematical operations between number types while operating on {@link Number}s.
 * <p>
 * Each operation has a method that is overridden as needed by each number type to ensure that
 * special behaviour is preserved (e.g. we cannot add two bytes as longs and then attempt to cast back
 * to byte because we need to make sure that the addition overflows as as <code>byte</code>; the same
 * applies to floats and doubles).
 */
public enum NumberType {
    BYTE(DataType.BYTE, Byte.class, false) {
        @Override
        public Number cast(Number in) {
            return in.byteValue();
        }

        @Override
        public Number divide(Number a, Number b) {
            return a.byteValue() / b.byteValue();
        }

        @Override
        public Number multiply(Number a, Number b) {
            return a.byteValue() * b.byteValue();
        }

        @Override
        public Number add(Number a, Number b) {
            return a.byteValue() + b.byteValue();
        }

        @Override
        public Number subtract(Number a, Number b) {
            return a.byteValue() - b.byteValue();
        }

        @Override
        public Number modulo(Number a, Number b) {
            return a.byteValue() % b.byteValue();
        }

        @Override
        public Number or(Number a, Number b) {
            return a.byteValue() | b.byteValue();
        }

        @Override
        public Number xor(Number a, Number b) {
            return a.byteValue() ^ b.byteValue();
        }

        @Override
        public Number and(Number a, Number b) {
            return a.byteValue() & b.byteValue();
        }

        @Override
        public Number lshift(Number a, Number b) {
            return a.byteValue() << b.byteValue();
        }

        @Override
        public Number rshift(Number a, Number b) {
            return a.byteValue() >> b.byteValue();
        }

        @Override
        public Number rshiftUnsigned(Number a, Number b) {
            return a.byteValue() >>> b.byteValue();
        }
    },
    SHORT(DataType.SHORT, Short.class, false, BYTE) {
        @Override
        public Number cast(Number in) {
            return in.shortValue();
        }

        @Override
        public Number divide(Number a, Number b) {
            return a.shortValue() / b.shortValue();
        }

        @Override
        public Number multiply(Number a, Number b) {
            return a.shortValue() * b.shortValue();
        }

        @Override
        public Number add(Number a, Number b) {
            return a.shortValue() + b.shortValue();
        }

        @Override
        public Number subtract(Number a, Number b) {
            return a.shortValue() - b.shortValue();
        }

        @Override
        public Number modulo(Number a, Number b) {
            return a.shortValue() % b.shortValue();
        }

        @Override
        public Number or(Number a, Number b) {
            return a.shortValue() | b.shortValue();
        }

        @Override
        public Number xor(Number a, Number b) {
            return a.shortValue() ^ b.shortValue();
        }

        @Override
        public Number and(Number a, Number b) {
            return a.shortValue() & b.shortValue();
        }

        @Override
        public Number lshift(Number a, Number b) {
            return a.shortValue() << b.shortValue();
        }

        @Override
        public Number rshift(Number a, Number b) {
            return a.shortValue() >> b.shortValue();
        }

        @Override
        public Number rshiftUnsigned(Number a, Number b) {
            return a.shortValue() >>> b.shortValue();
        }
    },
    INT(DataType.INT, Integer.class, true, SHORT) {
        @Override
        public long toUnsignedLong(Number number) {
            return Integer.toUnsignedLong(number.intValue());
        }

        @Override
        public Number cast(Number in) {
            return in.intValue();
        }
    },
    LONG(DataType.LONG, Long.class, true, INT) {
        @Override
        public Number negate(Number number) {
            return ~number.longValue();
        }

        @Override
        public Number cast(Number in) {
            return in.longValue();
        }

        @Override
        public Number divide(Number a, Number b) {
            return a.longValue() / b.longValue();
        }

        @Override
        public Number multiply(Number a, Number b) {
            return a.longValue() * b.longValue();
        }

        @Override
        public Number add(Number a, Number b) {
            return a.longValue() + b.longValue();
        }

        @Override
        public Number subtract(Number a, Number b) {
            return a.longValue() - b.longValue();
        }

        @Override
        public Number modulo(Number a, Number b) {
            return a.longValue() % b.longValue();
        }

        @Override
        public Number or(Number a, Number b) {
            return a.longValue() | b.longValue();
        }

        @Override
        public Number xor(Number a, Number b) {
            return a.longValue() ^ b.longValue();
        }

        @Override
        public Number and(Number a, Number b) {
            return a.longValue() & b.longValue();
        }

        @Override
        public Number lshift(Number a, Number b) {
            return a.longValue() << b.longValue();
        }

        @Override
        public Number rshift(Number a, Number b) {
            return a.longValue() >> b.longValue();
        }
        
        @Override
        public Number rshiftUnsigned(Number a, Number b) {
            return a.longValue() >>> b.longValue();
        }
    },
    FLOAT(DataType.FLOAT, Float.class, false, INT) {
        @Override
        public Number cast(Number in) {
            return in.floatValue();
        }

        @Override
        public Number divide(Number a, Number b) {
            return a.floatValue() / b.floatValue();
        }

        @Override
        public Number multiply(Number a, Number b) {
            return a.floatValue() * b.floatValue();
        }

        @Override
        public Number add(Number a, Number b) {
            return a.floatValue() + b.floatValue();
        }

        @Override
        public Number subtract(Number a, Number b) {
            return a.floatValue() - b.floatValue();
        }

        @Override
        public Number modulo(Number a, Number b) {
            return a.floatValue() % b.floatValue();
        }
    },
    DOUBLE(DataType.DOUBLE, Double.class, false, FLOAT) {
        @Override
        public Number cast(Number in) {
            return in.doubleValue();
        }

        @Override
        public Number divide(Number a, Number b) {
            return a.doubleValue() / b.doubleValue();
        }

        @Override
        public Number multiply(Number a, Number b) {
            return a.doubleValue() * b.doubleValue();
        }

        @Override
        public Number add(Number a, Number b) {
            return a.doubleValue() + b.doubleValue();
        }

        @Override
        public Number subtract(Number a, Number b) {
            return a.doubleValue() - b.doubleValue();
        }

        @Override
        public Number modulo(Number a, Number b) {
            return a.doubleValue() % b.doubleValue();
        }
    };

    public static final Map<Class<?>, NumberType> TYPES;
    static {
        var types = new HashMap<Class<?>, NumberType>();
        for (NumberType value : values()) {
            types.put(value.classType, value);
        }
        TYPES = Collections.unmodifiableMap(types);
    }

    public final DataType dataType;
    public final Class<?> classType;
    /**
     * Whether this number type can be treated as a bit flag - only {@code true} for {@link #INT} and {@link #LONG}.
     */
    public final boolean supportsFlag;
    /**
     * Number types that can be converted to this type without needing an explicit cast:
     * <p>
     * <code>byte</code> -> <code>short</code> -> <code>int</code> -> <code>long</code>
     * <p>
     * <code>int</code> -> <code>float</code> -> <code>double</code>
     */
    public final NumberType[] widenFrom;

    NumberType(DataType dataType, Class<?> classType, boolean supportsFlag, NumberType... widenFrom) {
        this.dataType = dataType;
        this.classType = classType;
        this.supportsFlag = supportsFlag;
        this.widenFrom = widenFrom;
    }

    public abstract Number cast(Number in);

    public long toUnsignedLong(Number number) {
        return number.longValue();
    }

    public Number negate(Number number) {
        return ~number.intValue();
    }

    public Number divide(Number a, Number b) {
        return a.intValue() / b.intValue();
    }

    public Number multiply(Number a, Number b) {
        return a.intValue() * b.intValue();
    }

    public Number add(Number a, Number b) {
        return a.intValue() + b.intValue();
    }

    public Number subtract(Number a, Number b) {
        return a.intValue() - b.intValue();
    }

    public Number modulo(Number a, Number b) {
        return a.intValue() % b.intValue();
    }

    public Number or(Number a, Number b) {
        return a.intValue() | b.intValue();
    }
    
    public Number xor(Number a, Number b) {
        return a.intValue() ^ b.intValue();
    }
    
    public Number and(Number a, Number b) {
        return a.intValue() & b.intValue();
    }

    public Number lshift(Number a, Number b) {
        return a.intValue() << b.intValue();
    }

    public Number rshift(Number a, Number b) {
        return a.intValue() >> b.intValue();
    }

    public Number rshiftUnsigned(Number a, Number b) {
        return a.intValue() >>> b.intValue();
    }

    public boolean canWidenFrom(NumberType other) {
        if (other == this) return true;
        for (NumberType from : widenFrom) {
            if (other == from || from.canWidenFrom(other)) {
                return true;
            }
        }
        return false;
    }

    public static NumberType widest(NumberType a, NumberType b) {
        return a.canWidenFrom(b) ? a : b;
    }
}
