package net.neoforged.jst.unpick;

import daomephsta.unpick.constantmappers.datadriven.tree.DataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum IntegerType {
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

    public static final Map<Class<?>, IntegerType> TYPES;
    static {
        var types = new HashMap<Class<?>, IntegerType>();
        for (IntegerType value : values()) {
            types.put(value.classType, value);
        }
        TYPES = Collections.unmodifiableMap(types);
    }

    public final DataType dataType;
    public final Class<?> classType;
    public final boolean supportsFlag;
    public final IntegerType[] widenFrom;

    IntegerType(DataType dataType, Class<?> classType, boolean supportsFlag, IntegerType... widenFrom) {
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
}
