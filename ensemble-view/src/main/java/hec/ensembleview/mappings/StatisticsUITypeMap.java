package hec.ensembleview.mappings;

import hec.ensemble.stats.Statistics;

import java.util.HashMap;
import java.util.Map;

public class StatisticsUITypeMap {
    public static final Map<Statistics, StatisticUIType> map = new HashMap<>();

    static {
        map.put(Statistics.MAX, StatisticUIType.CHECKBOX);
        map.put(Statistics.MIN, StatisticUIType.CHECKBOX);
        map.put(Statistics.AVERAGE, StatisticUIType.CHECKBOX);
        map.put(Statistics.MEDIAN, StatisticUIType.CHECKBOX);
        map.put(Statistics.TOTAL, StatisticUIType.CHECKBOX);
        map.put(Statistics.STANDARDDEVIATION, StatisticUIType.CHECKBOX);
        map.put(Statistics.VARIANCE, StatisticUIType.CHECKBOX);
        map.put(Statistics.CUMULATIVE, StatisticUIType.RADIOBUTTON);
        map.put(Statistics.PLOTTINGPOSITION, StatisticUIType.RADIOBUTTON);
        map.put(Statistics.NONE, StatisticUIType.RADIOBUTTON);
        map.put(Statistics.PERCENTILES, StatisticUIType.TEXTBOX);
        map.put(Statistics.MAXAVERAGEDURATION, StatisticUIType.TEXTBOX);
        map.put(Statistics.MAXACCUMDURATION, StatisticUIType.TEXTBOX);
        map.put(Statistics.NDAYCOMPUTABLE, StatisticUIType.TEXTBOX);
    }
}

