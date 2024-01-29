import cn.hutool.http.Header;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RangeQuery12306 {
    private final static AtomicLong atomicLong = new AtomicLong(0);
    private final static String cookie = "";
    private final static String host = "kyfw.12306.cn";
    private final static String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final static Map<String, String> stationName2CodeMap = new HashMap<>();
    private final static Map<String, String> stationCode2NameMap = new HashMap<>();

    static {
        try {
            blockedLock();
            String body = HttpUtil.get("https://kyfw.12306.cn/otn/resources/js/framework/station_name.js");
            String[] split = body.split("@");
            for (int i = 1; i < split.length; i++) {
                String[] split1 = split[i].split("\\|");
                String chineseName = split1[1];
                String threeCode = split1[2];
                stationName2CodeMap.put(chineseName, threeCode);
                stationCode2NameMap.put(threeCode, chineseName);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        List<Ticket> allTicket = 根据地点时间查询所有长短程车票("上海", "哈尔滨", "2024-02-07");

        for (Ticket ticket : allTicket) {
            System.out.println(ticket);
        }

    }

    private static List<Ticket> 根据地点时间查询所有长短程车票(String from, String to, String date) throws InterruptedException {
        from = stationName2CodeMap.get(from);
        to = stationName2CodeMap.get(to);

        List<Ticket> allTicket = new ArrayList<>();

        List<Ticket> tickets = 根据地点时间查询车票(from, to, date);
//        tickets.forEach(System.out::println);
//        System.out.println();

        for (Ticket ticket : tickets) {
            List<Station> stations = 根据车次查询所有站(ticket.trainNoFull, from, to, date);
//            stations.forEach(System.out::println);
//            System.out.println();

            Station stationNoGt = null;
            try {
                String finalFrom = from;
                stationNoGt = stations.stream().filter(x -> x.getStationCode().equals(finalFrom)).findFirst().get();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (Station station : stations) {
                if (Integer.parseInt(station.getStationNo()) <= Integer.parseInt(stationNoGt.getStationNo())) {
//                    System.out.println("之前的站，跳过：" + station.getStationName());
                    continue;
                }
                Ticket ticket1 = 根据地点时间查询车票(from, stationName2CodeMap.get(station.getStationName()), date, ticket.getTrainNoFull());
//                System.out.println(ticket1);
//                System.out.println();
                allTicket.add(ticket1);
            }
        }

        return allTicket;
    }

    private static List<Ticket> 根据地点时间查询车票(String from, String to, String date) throws InterruptedException {
        List<Ticket> tickets = new ArrayList<>();

        blockedLock();
        String url = "https://kyfw.12306.cn/otn/leftTicket/queryE?leftTicketDTO.train_date=" + date + "&leftTicketDTO.from_station=" + from + "&leftTicketDTO.to_station=" + to + "&purpose_codes=ADULT";
        System.out.println(url);
        String body = HttpUtil.createGet(url)
                .header(Header.COOKIE, cookie)
                .header(Header.HOST, host)
                .header(Header.USER_AGENT, ua)
                .execute().body();
        JSONObject jsonObject = JSON.parseObject(body);
        for (Object o : jsonObject.getJSONObject("data").getJSONArray("result")) {
            String result = (String) o;
            String[] split = result.split("\\|");
            String seatType_2 = split[30];
            String seatType_0 = split[26];
            String startTime = split[8];
            String endTime = split[9];
            String duration = split[10];
            String trainNoFull = split[2];
            String trainNo = split[3];
            // 起点站
            String fromStation = split[4];
            // 终点站
            String endStation = split[5];
            // 出发站
            String startStation = split[6];
            // 到达站
            String arriveStation = split[7];
            if (!trainNo.startsWith("G")) {
//                System.out.println("非高铁，跳过" + trainNo);
                continue;
            }
            if (!from.equals(startStation)) {
//                System.out.println("非同一站点，跳过" + trainNo + "， 出发站：" + stationCode2NameMap.get(startStation) + "，查询站：" + stationCode2NameMap.get(from));
                continue;
            }
            tickets.add(new Ticket(trainNoFull, trainNo, from, to, date, startTime, endTime, duration, seatType_0, seatType_2));
        }

        return tickets;
    }

    private static Ticket 根据地点时间查询车票(String from, String to, String date, String trainNo) throws InterruptedException {
        List<Ticket> tickets = 根据地点时间查询车票(from, to, date);

        for (Ticket ticket : tickets) {
            if (trainNo.equals(ticket.getTrainNoFull())) {
                return ticket;
            }
        }

        throw new RuntimeException("未找到指定车次：" + trainNo + "，请检查车次是否正确");
    }

    private static List<Station> 根据车次查询所有站(String trainNoFull, String from, String to, String date) throws InterruptedException {
        List<Station> stations = new ArrayList<>();

        blockedLock();
        String url = "https://kyfw.12306.cn/otn/czxx/queryByTrainNo?train_no=" + trainNoFull + "&from_station_telecode=" + from + "&to_station_telecode=" + to + "&depart_date=" + date;
        System.out.println(url);
        String body = HttpUtil.createGet(url)
                .header(Header.COOKIE, cookie)
                .header(Header.HOST, host)
                .header(Header.USER_AGENT, ua)
                .execute().body();
        JSONObject jsonObject = JSON.parseObject(body);
        int i = 0;
        for (Object o : jsonObject.getJSONObject("data").getJSONArray("data")) {
            JSONObject jsonObject1 = (JSONObject) o;
            String stationName = jsonObject1.getString("station_name");
            String stationNo = jsonObject1.getString("station_no");
            String arriveTime = jsonObject1.getString("arrive_time");
            String startTime = jsonObject1.getString("start_time");
            String stopoverTime = jsonObject1.getString("stopover_time");
            stations.add(new Station(stationName, stationName2CodeMap.get(stationName), stationNo, arriveTime, startTime, stopoverTime));
        }

        return stations;
    }

    private static final Random random = new Random();

    private static synchronized void blockedLock() throws InterruptedException {
        long l1 = atomicLong.get();
        long l;
        while ((l = System.currentTimeMillis()) - l1 <= 1200) {
            TimeUnit.MILLISECONDS.sleep(100 + random.nextInt(50));
//            System.out.println("歇一会");
        }
        atomicLong.set(l);
    }

    @Data
    @AllArgsConstructor
    static class Ticket {
        String trainNoFull;
        String trainNo;
        String from;
        String to;
        String date;
        String startTime;
        String endTime;
        String duration;
        // 无座
        String seatType_0;
        // 二等座
        String seatType_2;

        @Override
        public String toString() {
            return "车次：" + trainNo + "，出发站：" + stationCode2NameMap.get(from) + "，到达站：" + stationCode2NameMap.get(to) + "，日期：" + date + "，出发时间：" + startTime + "，到达时间：" + endTime + "，历时：" + duration + "，无座：" + seatType_0 + "，二等座：" + seatType_2;
        }
    }

    @Data
    @AllArgsConstructor
    static class Station {
        String stationName;
        String stationCode;
        String stationNo;
        String arriveTime;
        String startTime;
        String stopoverTime;
    }

}
