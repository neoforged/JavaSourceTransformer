package net.neoforged.jst.unpick;

import net.earthcomputer.unpickv3parser.tree.DataType;

public enum IntegerType {
    BYTE(DataType.BYTE, false) {
        @Override
        public Number cast(Number in) {
            return in.byteValue();
        }
    },
    SHORT(DataType.SHORT, false, BYTE) {
        @Override
        public Number cast(Number in) {
            return in.shortValue();
        }
    },
    INT(DataType.INT, true, SHORT) {
        @Override
        public long toUnsignedLong(Number number) {
            return Integer.toUnsignedLong(number.intValue());
        }

        @Override
        public Number negate(Number number) {
            return ~number.intValue();
        }

        @Override
        public Number cast(Number in) {
            return in.intValue();
        }
    },
    LONG(DataType.LONG, true, INT) {
        @Override
        public Number cast(Number in) {
            return in.longValue();
        }
    },
    FLOAT(DataType.FLOAT, false, INT) {
        @Override
        public Number cast(Number in) {
            return in.floatValue();
        }
    },
    DOUBLE(DataType.DOUBLE, false, FLOAT) {
        @Override
        public Number cast(Number in) {
            return in.doubleValue();
        }
    };

    public final DataType dataType;
    public final boolean supportsFlag;
    public final IntegerType[] widenFrom;

    IntegerType(DataType dataType, boolean supportsFlag, IntegerType... widenFrom) {
        this.dataType = dataType;
        this.supportsFlag = supportsFlag;
        this.widenFrom = widenFrom;
    }

    public abstract Number cast(Number in);

    public long toUnsignedLong(Number number) {
        return number.longValue();
    }

    public Number negate(Number number) {
        return ~number.longValue();
    }
}
