package master2017.flink.detectors;

import master2017.flink.KeySelectors.VidHighwayWestboundKeySelector;
import master2017.flink.events.AverageSpeedViolationEvent;
import master2017.flink.events.CarEvent;
import master2017.flink.functions.filters.SegmentRangeFilterFunction;
import master2017.flink.functions.windows.AverageSpeedWindowFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class AverageSpeedLimitDetector extends Detector {
    Integer speedLimit;
    Integer startingSegment;
    Integer endingSegment;

    public AverageSpeedLimitDetector(
            String outputFolder,
            SingleOutputStreamOperator<CarEvent> carEventStream,
            Integer speedLimit,
            Integer startingSegment,
            Integer endingSegment) {
        super(outputFolder, carEventStream, "avgspeedfines.csv");
        this.speedLimit = speedLimit;
        this.startingSegment = startingSegment;
        this.endingSegment = endingSegment;
    }

    @Override
    public void processCarEventStream() {
        getCarEventStream()
                .filter(
                        new SegmentRangeFilterFunction(
                                startingSegment - 1,
                                endingSegment + 1
                        ) //the car has to complete the sectors, so the only way to know if they appear before and late
                )
                .assignTimestampsAndWatermarks(
                        new AscendingTimestampExtractor<CarEvent>() {
                            @Override
                            public long extractAscendingTimestamp(CarEvent carEvent) {
                                return carEvent.getTimestamp() * 1000;
                            }
                        }
                )
                .keyBy(new VidHighwayWestboundKeySelector())
                .window(EventTimeSessionWindows.withGap(Time.seconds(30)))
                .apply(new AverageSpeedWindowFunction(speedLimit, startingSegment, endingSegment))
                .map(new MapFunction<AverageSpeedViolationEvent, Tuple6<Long, Long, String, String, Integer, Double>>() {
                    @Override
                    public Tuple6<Long, Long, String, String, Integer, Double> map(AverageSpeedViolationEvent averageSpeedViolationEvent) throws Exception {
                        return averageSpeedViolationEvent.toTuple();
                    }
                })
                .writeAsCsv(getOutputCSVFilePath().toString())
        ;

    }
}
