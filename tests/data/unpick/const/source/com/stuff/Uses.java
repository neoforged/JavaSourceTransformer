package com.stuff;

public class Uses {
    public String fld = "1.21.4";

    void run() {
        String s = "1.21.4" + "2";

        float f = 3.141592653589793f;

        f = 1.0471975511965976f;

        double d = 3.141592653589793d; // PI unpick is strict float so this should not be replaced

        d = 2.5d; // but the other float unpick isn't so this double literal should be replaced

        System.out.println(Long.toHexString(70L));
    }
}
