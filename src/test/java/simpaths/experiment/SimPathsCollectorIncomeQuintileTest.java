package simpaths.experiment;

import org.junit.jupiter.api.Test;
import simpaths.data.statistics.WealthIncomeStatistics;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SimPathsCollectorIncomeQuintileTest {

    private static BenefitUnit unit(double income, boolean couple) {
        BenefitUnit unit = mock(BenefitUnit.class);
        when(unit.getI_yNonBenHhGrossAsinhNoNull()).thenReturn(income);
        when(unit.getMale()).thenReturn(mock(Person.class));
        if (couple) when(unit.getFemale()).thenReturn(mock(Person.class));
        return unit;
    }

    private static Method updater() throws Exception {
        Class<?> type = Class.forName("simpaths.experiment.SimPathsCollector$Ydses_c5");
        Method update = type.getDeclaredMethod("update");
        update.setAccessible(true);
        return update;
    }

    private static Object calculator(SimPathsCollector collector) throws Exception {
        Class<?> type = Class.forName("simpaths.experiment.SimPathsCollector$Ydses_c5");
        Constructor<?> constructor = type.getDeclaredConstructor(SimPathsCollector.class);
        constructor.setAccessible(true);
        return constructor.newInstance(collector);
    }

    @Test
    void weightsCouplesTwiceAndPreservesFirstYearAssignments() throws Exception {
        List<BenefitUnit> units = List.of(unit(1, false), unit(2, false), unit(3, false),
                unit(4, false), unit(5, false), unit(6, true), unit(7, true),
                unit(8, true), unit(9, true), unit(10, true));
        SimPathsModel model = mock(SimPathsModel.class);
        when(model.getBenefitUnits()).thenReturn(new LinkedHashSet<>(units));
        SimPathsCollector collector = new SimPathsCollector(model);
        WealthIncomeStatistics stats = new WealthIncomeStatistics();
        collector.setWealthIncomeStats(stats);

        Object calculator = calculator(collector);
        Method update = updater();
        update.invoke(calculator);

        assertEquals(3.0, stats.getYHhQuintilesC5P20());
        assertEquals(6.0, stats.getYHhQuintilesC5P40());
        assertEquals(7.0, stats.getYHhQuintilesC5P60());
        assertEquals(9.0, stats.getYHhQuintilesC5P80());
        units.forEach(unit -> verify(unit, never()).updateIncomeQuintile());

        update.invoke(calculator);
        units.forEach(unit -> verify(unit).updateIncomeQuintile());
    }

    @Test
    void tiedIncomesShareCutoffsAndUnitsWithoutAdultsDoNotCount() throws Exception {
        BenefitUnit childOnly = mock(BenefitUnit.class);
        when(childOnly.getI_yNonBenHhGrossAsinhNoNull()).thenReturn(-100.0);
        List<BenefitUnit> units = List.of(childOnly, unit(1, false), unit(2, true),
                unit(2, true), unit(3, false));
        SimPathsModel model = mock(SimPathsModel.class);
        when(model.getBenefitUnits()).thenReturn(new LinkedHashSet<>(units));
        SimPathsCollector collector = new SimPathsCollector(model);
        WealthIncomeStatistics stats = new WealthIncomeStatistics();
        collector.setWealthIncomeStats(stats);

        updater().invoke(calculator(collector));

        assertEquals(2.0, stats.getYHhQuintilesC5P20());
        assertEquals(2.0, stats.getYHhQuintilesC5P40());
        assertEquals(2.0, stats.getYHhQuintilesC5P60());
        assertEquals(2.0, stats.getYHhQuintilesC5P80());
    }
}