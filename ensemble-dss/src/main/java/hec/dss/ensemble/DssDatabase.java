package hec.dss.ensemble;

import hec.EnsembleDatabase;
import hec.MetricDatabase;
import hec.RecordIdentifier;
import hec.ensemble.Ensemble;
import hec.ensemble.EnsembleTimeSeries;
import hec.ensemble.stats.Statistics;
import hec.heclib.dss.DSSPathname;
import hec.heclib.dss.HecPairedData;
import hec.heclib.dss.HecTimeSeries;
import hec.heclib.util.HecTime;
import hec.io.PairedDataContainer;
import hec.io.TimeSeriesCollectionContainer;
import hec.io.TimeSeriesContainer;
import hec.metrics.MetricCollection;
import hec.metrics.MetricCollectionTimeSeries;
import org.apache.commons.lang.NotImplementedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DssDatabase implements EnsembleDatabase.
 * It is used to work with time-series of ensembles, and associated statistics.
 * Data is stored in a DSS file, using the  (C) Collections, Time (T),
 * and  Version (V) features of the DSS F-Part tag.
 *
 * Data is stored in DSS regular time series and paired data structures.
 *
 */
public class DssDatabase implements EnsembleDatabase,MetricDatabase {
    private String dssFileName;
    private Catalog catalog;
    private static final DateTimeFormatter dssDateFormat = DateTimeFormatter.ofPattern("ddMMMyyyy HHmm");
    private static final DateTimeFormatter startDateformatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter issueDateformatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    static String metricTimeseriesIdentifier = "MetricTimeseries";
    static String metricPairedDataIdentifier = "MetricPairedData";
    boolean CatalogIsUpToDate = false;

    public DssDatabase(String dssFileName){
        this.dssFileName= dssFileName;
    }

    private Catalog getCatalog()
    {
        Catalog cat = getCatalog(!CatalogIsUpToDate);
        CatalogIsUpToDate = true;
        return cat;

    }
    private Catalog getCatalog(boolean rebuild){
        if( this.catalog == null) {
            catalog = new Catalog(this.dssFileName);
        }
        else if(rebuild){
            catalog.update();
        }
        return catalog;
    }

    private static String getEPart(int minutes){
        int[] status = new int[1];
        String ePart = hec.heclib.util.Heclib.getEPartFromInterval( minutes, status );
        return ePart;
    }

    // blank A/location/parameter//interval/C:000003|T:YYYYMMDD-hhmm|V:YYYYMMDD-hhmmss| f-part extra  /

    /**
     * translation of ensemble information to a DSS Path
     * @param e Ensemble
     * @param memberNumber member of ensemble
     * @param recordID identifier
     * @return returns path
     */
    private static String buildDssPath(Ensemble e,int memberNumber, RecordIdentifier recordID ){

        int minutes = (int)e.getInterval().toMinutes();
        String ePart = getEPart(minutes);

        return "//"+recordID.location+"/"+recordID.parameter+"/"
                + "/"+ePart+"/"+buildFpart(memberNumber,
                  e.getStartDateTime(),e.getIssueDate())+"/";
    }

    private static String buildTimeSeriesStatPathName(RecordIdentifier timeSeriesIdentifier, Duration interval,
                                               ZonedDateTime startDateTime, ZonedDateTime issueDate, String stat) {
        return buildTimeSeriesStatPathName(timeSeriesIdentifier,interval,startDateTime,issueDate,stat,"");
    }

        private static String buildTimeSeriesStatPathName(RecordIdentifier timeSeriesIdentifier, Duration interval, ZonedDateTime startDateTime, ZonedDateTime issueDate, String stat,
                                               String fPartSuffix) {
        DSSPathname path = new DSSPathname();
        path.setAPart("");
        path.setBPart(timeSeriesIdentifier.location);
        path.setCPart(timeSeriesIdentifier.parameter + "-" + stat+"-" + metricTimeseriesIdentifier);
        path.setDPart("");
        path.setEPart(getEPart((int)interval.toMinutes()));
        path.setFPart(buildFpart(startDateTime, issueDate,fPartSuffix));
        return path.toString();
    }


    private String buildPairedDataStatPathName(RecordIdentifier timeSeriesIdentifier, String stat, ZonedDateTime startDateTime, ZonedDateTime issueDate) {
        DSSPathname path = new DSSPathname();
        path.setAPart("");
        path.setBPart(timeSeriesIdentifier.location);
        path.setCPart(timeSeriesIdentifier.parameter + "-" + stat + "-" + metricPairedDataIdentifier);
        path.setDPart("");
        path.setEPart("");
        path.setFPart(buildFpart(startDateTime, issueDate));
        return path.toString();
    }

    /**
     * Builds F part of DSS path
     *
     * C - value is 6 alphanumeric characters
     * T - T: timestamp where timestamp = time of forecast format YYYYMMDD-hhmm
     * N - string where string is the forecast name
     * V - timestamp where timestamp = version time YYYYMMDD-hhmmss
     * R - string where string is the CWMS Run ID.
     *     value is even number of characters and each 2-character pair
     *     must be either "--" or an upper or lower case alphabetic
     *     character followed by a digit character (not restricted to '0')

     * @param t - time stamp saved in T: tag in F part
     * @param v - version of ensemble - (issue_date)
     * @param  member - ensemble member number to be in C: tag in F part
     * @return returns f-part
     */
    private static String buildFpart(int member , ZonedDateTime t, ZonedDateTime v){
        return String.format("C:%06d", member)
                +"|T:"+startDateformatter.format(t)
                +"|V:"+issueDateformatter.format(v)+"|";
    }

    private static String buildFpart(ZonedDateTime t, ZonedDateTime v) {

        return "T:" + startDateformatter.format(t)
                + "|V:" + issueDateformatter.format(v) + "|";
    }
    private static String buildFpart(ZonedDateTime t, ZonedDateTime v, String suffix ) {

        return "T:" + startDateformatter.format(t)
                + "|V:" + issueDateformatter.format(v) + "|"+suffix;
    }
    public Ensemble getEnsemble(RecordIdentifier recordID, ZonedDateTime issue_time){
        List<DSSPathname> paths = catalog.getPaths(recordID, issue_time);
        TimeSeriesCollectionContainer tscc = getEnsembleCollection(paths);
        float[][] vals = getEnsembleValues(tscc);
        ZonedDateTime startDate = getStartDate(tscc);
        Duration interval = Duration.ofMinutes(hec.heclib.dss.HecTimeSeries.getIntervalFromEPart(paths.get(0).ePart()));

        return new Ensemble(
                issue_time,
                vals,
                startDate,
                interval,
                tscc.get(0).units
        );
    }

    private float[][] getEnsembleValues(TimeSeriesCollectionContainer tscc) {
        float[][] vals = new float[tscc.size()][];

        for (int i = 0; i < tscc.size(); i++) {
            vals[i] = convertDoublesToFloats(tscc.get(i).values);
        }

        return vals;
    }

    private ZonedDateTime getStartDate(TimeSeriesCollectionContainer tscc) {
        HecTime time = tscc.get(0).getStartTime();
        return getZonedDateTime(time);
    }

    static ZonedDateTime getZonedDateTime(HecTime time) {
        int hour = time.hour();
        // Currently, assume all dss time series records in GMT. Time will appear as shown in dss record.
        ZoneId timeZone = ZoneId.of("GMT");

        if (hour == 24) {
            String hecTimeStyle = time.toString(-13);
            String[] timeParse = hecTimeStyle.replaceAll("\\s*,\\s*", ",").split(",");
            String date = timeParse[0];
            String timeReset = "00:00:00";
            String formatDateTime = date + "T" + timeReset;
            LocalDateTime ldt = LocalDateTime.parse(formatDateTime);
            return ZonedDateTime.of(ldt, timeZone).plusDays(1);
        } else {
            return ZonedDateTime.of(time.year(), time.month(), time.day(), time.hour(),
                    time.minute(), time.second(), 0,
                    timeZone);
        }
    }

    private TimeSeriesCollectionContainer getEnsembleCollection(List<DSSPathname> paths) {
        TimeSeriesCollectionContainer tscc = new TimeSeriesCollectionContainer();
        HecTimeSeries dss = new HecTimeSeries();
        dss.setDSSFileName(this.dssFileName);

        for (int i = 0; i < paths.size(); i++) {
            TimeSeriesContainer tsc = new TimeSeriesContainer();
            tsc.setFullName(paths.get(i).pathname());
            dss.read(tsc, true);
            tscc.add(tsc);
        }

        dss.done();
        return tscc;
    }

    public EnsembleTimeSeries getEnsembleTimeSeries(RecordIdentifier recordID){
        EnsembleTimeSeries ets = new EnsembleTimeSeries(recordID, "", "", "");
        List<ZonedDateTime> zdts = getCatalog().rids.get(recordID);

        for (ZonedDateTime zdt : zdts) {
            Ensemble e = getEnsemble(recordID, zdt);
            ets.addEnsemble(e);
        }

        return ets;
    }

    /**
     * Return list of ZonedDateTime for a RecordIdentifier
     * from DSS f-part T: tag
     * TO DO?  mapping to T: of DSS (consider renaming to getEnsembleStartDates()
     *
     * @param recordID
     * @return
     */
    public java.util.List<java.time.ZonedDateTime> getEnsembleIssueDates(RecordIdentifier recordID) {
        return getCatalog().getEnsembleStartDates(recordID);
    }

    public void write(EnsembleTimeSeries[] etsArray) throws Exception{
        for (EnsembleTimeSeries ets: etsArray){
            write(ets);
        }
        CatalogIsUpToDate = false;
    }
    public void write(EnsembleTimeSeries ets) throws Exception{
        // get access to DSS
        HecTimeSeries dss = new hec.heclib.dss.HecTimeSeries(dssFileName);
        //translate EnsembleTimeSeries into TimeSeriesCollectionContainer
        TimeSeriesCollectionContainer containers = loadContainers(ets);
        // write TimeSeriesCollectionContainer to DSS file
        dss.write(containers);
        // close resources
        dss.close();
        CatalogIsUpToDate = false;
    }
    private static TimeSeriesCollectionContainer loadContainers(EnsembleTimeSeries ets){

        TimeSeriesCollectionContainer rval = new hec.io.TimeSeriesCollectionContainer();
        for (Ensemble e: ets){
            float[][] values = e.getValues();
            for (int row = 0; row <values.length ; row++) {
                TimeSeriesContainer tsc = new TimeSeriesContainer();
                tsc.setValues(convertFloatsToDoubles(values[row]));
                String path = buildDssPath(e,(row+1),ets.getTimeSeriesIdentifier());
                tsc.setFullName(path);
                tsc.units = e.getUnits();
                tsc.setType(ets.getDataType());
                tsc.setStartTime(getHecStartTime(e));
                rval.add(tsc);
            }
        }
        rval.finishedAdding();
        return rval;
    }


    private static HecTime getHecStartTime(Ensemble e) {
        String dateStr = "";
        if (e.getTimeCount() > 0)
            dateStr = e.startDateTime()[0].format(dssDateFormat);
        return new HecTime(dateStr);
    }

    /**
     * convert array of floats to array of doubles
     * //https://stackoverflow.com/questions/2019362/how-to-convert-array-of-floats-to-array-of-doubles-in-java
     * @param input
     * @return
     */

    private static double[] convertFloatsToDoubles(float[] input)
    {
        if (input == null)
        {
            return null; // Or throw an exception - your choice
        }
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++)
        {
            output[i] = input[i];
        }
        return output;
    }

    private static float[] convertDoublesToFloats(double[] input)
    {
        if (input == null)
        {
            return null; // Or throw an exception - your choice
        }
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++)
        {
            output[i] = (float)input[i];
        }
        return output;
    }

    /**
     * getEnsembleTimeSeriesIDs returns a list of RecordIdentifier's
     *
     * Limitations:
     *    - Assuming Interval is consistent for the whole DSS file
     *    - Assuming fpart beyond C:,T:, V: is ignored
     * @return
     */
    public java.util.List<RecordIdentifier> getEnsembleTimeSeriesIDs(){
        return getCatalog().getEnsembleTimeSeriesIDs();
    }

    @Override
    public String getFileName() {
        return this.dssFileName;
    }


    @Override
    public void close() throws Exception {

    }

    @Override
    public hec.metrics.MetricCollection getMetricCollection(hec.RecordIdentifier timeseriesID, java.time.ZonedDateTime issue_time) {
        List<DSSPathname> paths = getCatalog().getMetricPaths(timeseriesID, issue_time);

        String stats = getMetricStatsLabelsFromPaths(paths); // this does not work for paired data, because the stats labels are stored in the labels of the pdc. Need to access the pdc.
        float[][] values = getMetricValues(paths);
        MetricCollection mc = new MetricCollection(issue_time, issue_time, stats, values);
        return mc;
    }

    /*
    Commented this out for now to NotImplement new version

    @Override
    public hec.metrics.MetricCollectionTimeSeries getMetricCollectionTimeSeries(hec.RecordIdentifier timeseriesID) {
        MetricCollectionTimeSeries mcts = new MetricCollectionTimeSeries(timeseriesID, "", MetricTypes.TIMESERIES_OF_ARRAY);
        for (ZonedDateTime zdt : getCatalog().getMetricIssueDates(timeseriesID)) {
            List<DSSPathname> paths = getCatalog().getMetricPaths(timeseriesID, zdt);
            String stats = getMetricStatsLabelsFromPaths(paths);
            float[][] values = getMetricValues(paths);
            MetricCollection mc = new MetricCollection(zdt, zdt, stats, values);
            mcts.addMetricCollection(mc);
        }
        return mcts;
    }*/

    @Override
    public List<hec.metrics.MetricCollectionTimeSeries> getMetricCollectionTimeSeries(hec.RecordIdentifier timeseriesID) throws NotImplementedException{
        throw new NotImplementedException();
    }

    @Override
    public hec.metrics.MetricCollectionTimeSeries getMetricCollectionTimeSeries(hec.RecordIdentifier timeseriesID, String statistic) throws NotImplementedException {
        throw new NotImplementedException();
    }

    @Override
    public Map<RecordIdentifier, List<String>> getMetricStatistics() throws NotImplementedException {
        throw new NotImplementedException();
    }

    @Override
    public List<String> getMetricStatistics(hec.RecordIdentifier timeseriesID) throws NotImplementedException {
        throw new NotImplementedException();
    }

    private float[][] getMetricValues(List<DSSPathname> paths) {
        HecTimeSeries dss = new HecTimeSeries(dssFileName);
        List<float[]> values = new ArrayList<>();
        for (DSSPathname path : paths) {
            TimeSeriesContainer tsc = new TimeSeriesContainer();
            tsc.fullName = path.toString();
            dss.read(tsc, true);
            values.add(convertDoublesToFloats(tsc.values));
        }

        dss.done();

        return values.toArray(new float[0][0]);
    }

    private Statistics[] getMetricStatsFromPaths(List<DSSPathname> paths) {
        Statistics[] stats = new Statistics[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            stats[i] = MetricPathTools.getMetricStatFromPath(paths.get(i).toString());
        }
        return stats;
    }

    private String getMetricStatsLabelsFromPaths(List<DSSPathname> paths) {
        String stats = "";
        for (int i = 0; i < paths.size(); i++) {
            stats += MetricPathTools.getMetricStatLabelFromPath(paths.get(i));
            if(i != paths.size()-1){
                stats+= "|";
            }
        }
        return stats;
    }

    @Override
    public java.util.List<java.time.ZonedDateTime> getMetricCollectionIssueDates(hec.RecordIdentifier timeseriesID) {
        return getCatalog().getMetricIssueDates(timeseriesID);
    }

    @Override
    public void write(hec.metrics.MetricCollectionTimeSeries[] metricsArray) throws Exception {
        for (MetricCollectionTimeSeries mcts : metricsArray)
            write(mcts);
        CatalogIsUpToDate = false;
    }

    /**
     * toTimeSeriesContainer returns a TimeSeriesContainer[] from a metricCollectionTimeSeries. The method is located in DssDatabase
     * rather than the MetricCollectionTimeSeries class in order to keep dependency on hec-monolith out of FIRO_TSEnsembles and
     * confine hec-monolith dependency to the ensemble-dss project.
     */
    public static TimeSeriesContainer[] toTimeSeriesContainer(MetricCollectionTimeSeries mcts) {
        List<TimeSeriesContainer> tscList = new ArrayList<>();
        for (MetricCollection mc : mcts) {
            String stats = mc.getMetricStatistics();
            String[] statsAsSeparateTimeseries = stats.split("\\|");

            for (int i = 0; i < statsAsSeparateTimeseries.length; i++) {
                TimeSeriesContainer tsc = convertToTimeSeriesContainer(mcts.getTimeSeriesIdentifier(), mc, statsAsSeparateTimeseries[i], mc.getValues()[i]);
                tscList.add(tsc);
            }
        }
        return tscList.toArray(new TimeSeriesContainer[0]);
    }
    
    private static TimeSeriesContainer convertToTimeSeriesContainer(RecordIdentifier identifier, MetricCollection mc, String stat, float[] values) {
        return convertToTimeSeriesContainer(identifier, mc, stat, values,"");
    }

    private static TimeSeriesContainer convertToTimeSeriesContainer(RecordIdentifier identifier, MetricCollection mc, String stat, float[] values, String fPartSuffix) {
        TimeSeriesContainer tsc = new TimeSeriesContainer();
        tsc.values = convertFloatsToDoubles(values);
        tsc.numberValues = tsc.values.length;
        HecTime time = getHecStartTime(mc.getStartDateTime());
        tsc.setStartTime(time);
        tsc.fullName = buildTimeSeriesStatPathName(identifier,
                mc.getInterval(),
                mc.getStartDateTime(),
                mc.getIssueDate(),
                stat,
                fPartSuffix);
        return tsc;
    }

    @Override
    public void write(hec.metrics.MetricCollectionTimeSeries metrics) throws Exception {
       write(metrics,"");
    }
    public void write(hec.metrics.MetricCollectionTimeSeries metrics, String fPartSuffix) throws Exception {
        HecTimeSeries dss = new HecTimeSeries(dssFileName);
        for (MetricCollection mc : metrics) {
            String stats = mc.getMetricStatistics();
            String[] statsAsSeparateTimeseries = stats.split("\\|");

            for (int i = 0; i < statsAsSeparateTimeseries.length; i++) {
                TimeSeriesContainer tsc = convertToTimeSeriesContainer(metrics.getTimeSeriesIdentifier(), mc, statsAsSeparateTimeseries[i], mc.getValues()[i], fPartSuffix);
                dss.write(tsc);
            }
        }
        dss.done();
        CatalogIsUpToDate = false;
    }

    public void write(MetricCollection metrics) {
        HecPairedData dss = new HecPairedData(dssFileName);
        String stats = metrics.getMetricStatistics();
        String[] statsAsSeparateSeries = stats.split("\\|");

        PairedDataContainer originalPdc = new PairedDataContainer();
        originalPdc.yOrdinates = getYOrdinates(metrics.getValues());
        originalPdc.xOrdinates = getXOrdinates(metrics.getValues()[0].length);
        originalPdc.labels = statsAsSeparateSeries;
        originalPdc.numberCurves = originalPdc.yOrdinates.length;
        originalPdc.numberOrdinates = originalPdc.xOrdinates.length;

        for (int i = 0; i < originalPdc.yOrdinates.length; i++) {
            PairedDataContainer singlePdc = new PairedDataContainer();
            singlePdc.yOrdinates = new double[1][];
            singlePdc.yOrdinates[0] = originalPdc.yOrdinates[i];
            singlePdc.xOrdinates = originalPdc.xOrdinates;
            singlePdc.labels = new String[]{statsAsSeparateSeries[i]};
            singlePdc.numberCurves = 1;
            singlePdc.numberOrdinates = singlePdc.xOrdinates.length;
            singlePdc.labelsUsed = true;
            singlePdc.fullName = buildPairedDataStatPathName(metrics.parent.getTimeSeriesIdentifier(), singlePdc.labels[0],
                    metrics.getStartDateTime(),
                    metrics.getIssueDate());
            dss.write(singlePdc);
        }

        dss.done();
        CatalogIsUpToDate = false;
    }

    private double[] getXOrdinates(int length) {
        double[] res = new double[length];
        for (int i = 0; i < length; i++) {
            res[i] = i + 1;
        }
        return res;
    }

    private double[][] getYOrdinates(float[][] values) {
        double[][] res = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            res[i] = convertFloatsToDoubles(values[i]);
        }
        return res;
    }

    private static HecTime getHecStartTime(ZonedDateTime startDateTime) {
        String dateStr = "";
        dateStr = startDateTime.format(dssDateFormat);
        return new HecTime(dateStr);
    }

    @Override
    public java.util.List<hec.RecordIdentifier> getMetricTimeSeriesIDs() {
        return getCatalog().getMetricTimeSeriesIDs();
    }

    @Override
    public List<RecordIdentifier> getMectricPairedDataIDs() {
        return getCatalog().getMetricPairedDataIDs();
    }
}