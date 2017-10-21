import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.demo.charts.ExampleChart;
import org.json.*;

class RealtimeChart implements ExampleChart<XYChart> {

    private XYChart xyChart;

    private List<Date> xData = new LinkedList<Date>();
    private final List<Double> yData = new LinkedList<Double>();


    public static final String SERIES_NAME = "series1";

    public String title;

    public RealtimeChart(String title) {
        super();
        this.title = title;
    }

    public XChartPanel<XYChart> buildPanel() {

        return new XChartPanel<XYChart>(getChart());
    }

    @Override
    public XYChart getChart() {

        for (int i = 0; i < 40; i++) {
            if(i <= 9) {
                yData.add((double)i);
            }else
                yData.add(0.0);
        }

        // Create Chart
        xyChart = new XYChartBuilder().width(500).height(400).xAxisTitle("Time").yAxisTitle("RAIE").title(title).build();
        xyChart.getStyler().setXAxisTicksVisible(false);
        xyChart.getStyler().setLegendVisible(false);
        xyChart.addSeries(SERIES_NAME, null, yData);

        return xyChart;
    }


    public void updateData(double raie) {
        yData.add(raie);
        yData.remove(0);
        xyChart.updateXYSeries(SERIES_NAME, null, yData, null);
    }
}

/*
JSON格式：
{
"vm_cnt":2,
"vm_info": [
        {
            "vm_name": "vm1",
            "raie": 5.8,
            "timestamp": 1507982071146
        },
        {
            "vm_name": "vm2",
            "raie": 4.7,
            "timestamp": 1507982071149
        }
    ]
}
 */

class VMInfo{
    public String vmName;
    public double raie;
    public long timeStamp;

    public VMInfo() {}
    public VMInfo(String vmName, double raie, long timeStamp) {
        this.vmName = vmName;
        this.raie = raie;
        this.timeStamp = timeStamp;
    }

}
public class RealTimeDisplay {
    private static final String VM_CNT_KEY = "vm_cnt";
    private static final String VM_INFO_KEY = "vm_info";
    private static final String VM_NAME_KEY = "vm_name";
    private static final String VM_RAIE_KEY = "raie";
    private static final String VM_TIMESTAMP_KEY = "timestamp";
    private final String PANEL_NAME = "Real Time VM Info Display";

    private final String ip;
    private final int port;
    private Socket socket = null;
    private ConcurrentHashMap<String, BlockingDeque<VMInfo>> vminfoQue = new ConcurrentHashMap<>();

    public RealTimeDisplay(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    private void setUpChart(final String name) {
        // Setup the panel
        final RealtimeChart realtimeChart = new RealtimeChart(name);
        final XChartPanel<XYChart> chartPanel = realtimeChart.buildPanel();

        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {

                // Create and set up the window.
                JFrame frame = new JFrame(PANEL_NAME);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.add(chartPanel);

                // Display the window.
                frame.pack();
                frame.setVisible(true);
            }
        });

        // Simulate a data feed
        TimerTask chartUpdaterTask = new TimerTask() {

            @Override
            public void run() {
                double raie = 0;
                try {
                    VMInfo vminfo = vminfoQue.get(name).take();
                    raie = vminfo.raie;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                realtimeChart.updateData(raie);
                chartPanel.revalidate();
                chartPanel.repaint();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(chartUpdaterTask, 0, 200);
    }
    private void parseJSON(final String jsonStr){
        if(jsonStr == null || jsonStr.equals(""))
            return;
        JSONObject jobj = new JSONObject(jsonStr);
        int vmCnt = jobj.getInt(VM_CNT_KEY);
        JSONArray jarray = jobj.getJSONArray(VM_INFO_KEY);
        for(int i = 0; i < jarray.length(); ++i) {
            final JSONObject vmObj = jarray.getJSONObject(i);
            final String name = vmObj.getString(VM_NAME_KEY);
            final long ts = vmObj.getLong(VM_TIMESTAMP_KEY);
            final double raie = vmObj.getDouble(VM_RAIE_KEY);
            if(!vminfoQue.containsKey(name)) {
                vminfoQue.put(name, new LinkedBlockingDeque<>(128));
                setUpChart(name);
            }
            try {
                vminfoQue.get(name).put(new VMInfo(name, raie, ts));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void connect() {
        while(true) {
            try {
                SocketAddress sockaddr = new InetSocketAddress(ip, port);
                socket = new Socket();
                socket.connect(sockaddr);
            }catch (Exception e) {
                System.out.println("Connect "+ip+":"+port+" failed. "+e.getMessage());
                try {
                    Thread.currentThread().sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                continue;
            }
            break;
        }
        System.out.println("Connect to "+ip+":"+port);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //System.out.println("IsConnected:" + socket.isConnected());
                    //读取服务器端数据
                    BufferedReader bufReader =  new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line = null;
                    while((line = bufReader.readLine()) !=null) {
                        System.out.println("Get msg:" + line);
                        parseJSON(line);
                    }
                } catch (Exception e) {
                    System.out.println("客户端异常:" + e.getMessage());
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            socket = null;
                            System.out.println("客户端 finally 异常:" + e.getMessage());
                        }
                    }
                }
            }
        }).start();
    }

    public void start() {
        connect();

    }

    public static void main(String[] args) {
        String ip = "localhost";
        int port = 6666;
        if(args.length == 2) {
            ip = args[0];
            port = Integer.parseInt(args[1]);
        }

        new RealTimeDisplay(ip, port).start();

    }
}
