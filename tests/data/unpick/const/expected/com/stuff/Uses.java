package com.stuff;

import com.example.Constants;

public class Uses {
    public String fld = Constants.VERSION;

    void run() {
        String s = Constants.VERSION + "2";

        float f = Math.PI;

        f = Math.PI / 3;

        double d = 3.141592653589793d; // PI unpick is strict float so this should not be replaced

        d = Constants.FLOAT_CT; // but the other float unpick isn't so this double literal should be replaced

        System.out.println(Long.toHexString((Constants.LONG_VAL + 1) * 2));
    }
}
