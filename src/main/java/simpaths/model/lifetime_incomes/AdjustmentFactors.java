package simpaths.model.lifetime_incomes;

public class AdjustmentFactors {

    public static double fixedEffect = 0.5;

    public static double[] whiteNoise = {
            0.0000	,        // age0
            0.0000	,        // age1
            0.0000	,        // age2
            0.0000	,        // age3
            0.0000	,        // age4
            0.0000	,        // age5
            0.0000	,        // age6
            0.0000	,        // age7
            0.0000	,        // age8
            0.0000	,        // age9
            0.0000	,        // age10
            0.0000	,        // age11
            0.0000	,        // age12
            0.0000	,        // age13
            0.0000	,        // age14
            0.0000	,        // age15
            0.0000	,        // age16
            0.0000	,        // age17
            0.0000	,        // age18
            0.0000	,        // age19
            0.0000	,        // age20
            0.0000	,        // age21
            0.0000	,        // age22
            0.0000	,        // age23
            0.0000	,        // age24
            0.0000	,        // age25
            0.0000	,        // age26
            0.0000	,        // age27
            0.0000	,        // age28
            0.0000	,        // age29
            0.0000	,        // age30
            0.0000	,        // age31
            0.0000	,        // age32
            0.0000	,        // age33
            0.0000	,        // age34
            0.0000	,        // age35
            0.0000	,        // age36
            0.0000	,        // age37
            0.0000	,        // age38
            0.0000	,        // age39
            0.0000	,        // age40
            0.0000	,        // age41
            0.0000	,        // age42
            0.0000	,        // age43
            0.0000	,        // age44
            0.0000	,        // age45
            0.0000	,        // age46
            0.0000	,        // age47
            0.0000	,        // age48
            0.0000	,        // age49
            0.0000	,        // age50
            0.0000	,        // age51
            0.0000	,        // age52
            0.0000	,        // age53
            0.0000	,        // age54
            0.0000	,        // age55
            0.0000	,        // age56
            0.0000	,        // age57
            0.0000	,        // age58
            0.0000	,        // age59
            0.0000	,        // age60
            0.0000	,        // age61
            0.0000	,        // age62
            0.0000	,        // age63
            0.0000	,        // age64
            0.0000	,        // age65
            0.0000	,        // age66
            0.0000	,        // age67
            0.0000	,        // age68
            0.0000	,        // age69
            0.0000	,        // age70
            0.0000	,        // age71
            0.0000	,        // age72
            0.0000	,        // age73
            0.0000	,        // age74
            0.0000	,        // age75
            0.0000	,        // age76
            0.0000	,        // age77
            0.0000	,        // age78
            0.0000	,        // age79
            0.0000	,        // age80
    };

    public static double getWhiteNoise(int age) {
        return whiteNoise[age];
    }
}
