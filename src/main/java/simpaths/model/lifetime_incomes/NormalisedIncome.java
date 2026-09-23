package simpaths.model.lifetime_incomes;

import jakarta.persistence.*;
import microsim.statistics.IDoubleSource;
import simpaths.data.Parameters;
import simpaths.model.enums.Gender;
import simpaths.model.enums.TimeSeriesVariable;

public class NormalisedIncome implements IDoubleSource {

    TemplateIndividual individual;
    private int age;
    private double zValue;


    /**
     * CONSTRUCTOR
     */
    public NormalisedIncome() {}

    public NormalisedIncome( TemplateIndividual individual, int age, double val) {
        this.individual = individual;
        this.age = age;
        if (age == 0) {

            zValue = val;
        } else {

            double z_score = Parameters.getRegLifetimeIncome2a().getScore(this, DoublesVariables.class);    // dynamics
            z_score += Parameters.getRegLifetimeIncome2b().getScore(this, DoublesVariables.class);          // age effect
            zValue = z_score + individual.getFixedEffect() + val;                                                  // residual (fixed effect + white noise)
        }
    }


    /**
     * GETTERS AND SETTERS
     */
    public int getAge() {return age;}
    public Double getZValue() {return zValue;}
    public TemplateIndividual getIndividual() {return individual;}
    public void setZValue(Double zValue) {this.zValue = zValue;}

    /**
     * WORKING METHODS
     */

    public enum DoublesVariables {

        Age0,
        Age1,
        Age2,
        Age3,
        Age4,
        Age5,
        Age6,
        Age7,
        Age8,
        Age9,
        Age10,
        Age11,
        Age12,
        Age13,
        Age14,
        Age15,
        Age16,
        Age17,
        Age18,
        Age19,
        Age20,
        Age21,
        Age22,
        Age23,
        Age24,
        Age25,
        Age26,
        Age27,
        Age28,
        Age29,
        Age30,
        Age31,
        Age32,
        Age33,
        Age34,
        Age35,
        Age36,
        Age37,
        Age38,
        Age39,
        Age40,
        Age41,
        Age42,
        Age43,
        Age44,
        Age45,
        Age46,
        Age47,
        Age48,
        Age49,
        Age50,
        Age51,
        Age52,
        Age53,
        Age54,
        Age55,
        Age56,
        Age57,
        Age58,
        Age59,
        Age60,
        Age61,
        Age62,
        Age63,
        Age64,
        Age65,
        Age66,
        Age67,
        Age68,
        Age69,
        Age70,
        Age71,
        Age72,
        Age73,
        Age74,
        Age75,
        Age76,
        Age77,
        Age78,
        Age79,
        Age80,
        Age81,
        Age82,
        Age83,
        Age84,
        Age85plus,
        z_lag1,
        z_lag2
    }
    public double getDoubleValue(Enum<?> variableID) {

        switch ((DoublesVariables) variableID) {
            case Age0 -> {return (age==0) ? 1: 0;}
            case Age1 -> {return (age==1) ? 1: 0;}
            case Age2 -> {return (age==2) ? 1: 0;}
            case Age3 -> {return (age==3) ? 1: 0;}
            case Age4 -> {return (age==4) ? 1: 0;}
            case Age5 -> {return (age==5) ? 1: 0;}
            case Age6 -> {return (age==6) ? 1: 0;}
            case Age7 -> {return (age==7) ? 1: 0;}
            case Age8 -> {return (age==8) ? 1: 0;}
            case Age9 -> {return (age==9) ? 1: 0;}
            case Age10 -> {return (age==10) ? 1: 0;}
            case Age11 -> {return (age==11) ? 1: 0;}
            case Age12 -> {return (age==12) ? 1: 0;}
            case Age13 -> {return (age==13) ? 1: 0;}
            case Age14 -> {return (age==14) ? 1: 0;}
            case Age15 -> {return (age==15) ? 1: 0;}
            case Age16 -> {return (age==16) ? 1: 0;}
            case Age17 -> {return (age==17) ? 1: 0;}
            case Age18 -> {return (age==18) ? 1: 0;}
            case Age19 -> {return (age==19) ? 1: 0;}
            case Age20 -> {return (age==20) ? 1: 0;}
            case Age21 -> {return (age==21) ? 1: 0;}
            case Age22 -> {return (age==22) ? 1: 0;}
            case Age23 -> {return (age==23) ? 1: 0;}
            case Age24 -> {return (age==24) ? 1: 0;}
            case Age25 -> {return (age==25) ? 1: 0;}
            case Age26 -> {return (age==26) ? 1: 0;}
            case Age27 -> {return (age==27) ? 1: 0;}
            case Age28 -> {return (age==28) ? 1: 0;}
            case Age29 -> {return (age==29) ? 1: 0;}
            case Age30 -> {return (age==30) ? 1: 0;}
            case Age31 -> {return (age==31) ? 1: 0;}
            case Age32 -> {return (age==32) ? 1: 0;}
            case Age33 -> {return (age==33) ? 1: 0;}
            case Age34 -> {return (age==34) ? 1: 0;}
            case Age35 -> {return (age==35) ? 1: 0;}
            case Age36 -> {return (age==36) ? 1: 0;}
            case Age37 -> {return (age==37) ? 1: 0;}
            case Age38 -> {return (age==38) ? 1: 0;}
            case Age39 -> {return (age==39) ? 1: 0;}
            case Age40 -> {return (age==40) ? 1: 0;}
            case Age41 -> {return (age==41) ? 1: 0;}
            case Age42 -> {return (age==42) ? 1: 0;}
            case Age43 -> {return (age==43) ? 1: 0;}
            case Age44 -> {return (age==44) ? 1: 0;}
            case Age45 -> {return (age==45) ? 1: 0;}
            case Age46 -> {return (age==46) ? 1: 0;}
            case Age47 -> {return (age==47) ? 1: 0;}
            case Age48 -> {return (age==48) ? 1: 0;}
            case Age49 -> {return (age==49) ? 1: 0;}
            case Age50 -> {return (age==50) ? 1: 0;}
            case Age51 -> {return (age==51) ? 1: 0;}
            case Age52 -> {return (age==52) ? 1: 0;}
            case Age53 -> {return (age==53) ? 1: 0;}
            case Age54 -> {return (age==54) ? 1: 0;}
            case Age55 -> {return (age==55) ? 1: 0;}
            case Age56 -> {return (age==56) ? 1: 0;}
            case Age57 -> {return (age==57) ? 1: 0;}
            case Age58 -> {return (age==58) ? 1: 0;}
            case Age59 -> {return (age==59) ? 1: 0;}
            case Age60 -> {return (age==60) ? 1: 0;}
            case Age61 -> {return (age==61) ? 1: 0;}
            case Age62 -> {return (age==62) ? 1: 0;}
            case Age63 -> {return (age==63) ? 1: 0;}
            case Age64 -> {return (age==64) ? 1: 0;}
            case Age65 -> {return (age==65) ? 1: 0;}
            case Age66 -> {return (age==66) ? 1: 0;}
            case Age67 -> {return (age==67) ? 1: 0;}
            case Age68 -> {return (age==68) ? 1: 0;}
            case Age69 -> {return (age==69) ? 1: 0;}
            case Age70 -> {return (age==70) ? 1: 0;}
            case Age71 -> {return (age==71) ? 1: 0;}
            case Age72 -> {return (age==72) ? 1: 0;}
            case Age73 -> {return (age==73) ? 1: 0;}
            case Age74 -> {return (age==74) ? 1: 0;}
            case Age75 -> {return (age==75) ? 1: 0;}
            case Age76 -> {return (age==76) ? 1: 0;}
            case Age77 -> {return (age==77) ? 1: 0;}
            case Age78 -> {return (age==78) ? 1: 0;}
            case Age79 -> {return (age==79) ? 1: 0;}
            case Age80 -> {return (age==80) ? 1: 0;}
            case Age81 -> {return (age==81) ? 1: 0;}
            case Age82 -> {return (age==82) ? 1: 0;}
            case Age83 -> {return (age==83) ? 1: 0;}
            case Age84 -> {return (age==84) ? 1: 0;}
            case Age85plus -> {return (age>84) ? 1: 0;}
            case z_lag1 -> {return (age>0) ? individual.getNormIncome(age-1).getZValue(): 0;}
            case z_lag2 -> {return (age>1) ? individual.getNormIncome(age-2).getZValue(): 0;}
            default -> {
                throw new RuntimeException("request for unrecognised variable");
            }
        }
    }
}
