package com.whu.yun.utils;
import com.whu.yun.entity.FlyPoint;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
//import com.dji.GSDemo.GaodeMap.Waypoint1Activity;


@Component
public class FlyPlan_GBK {

    public FlyPlan_GBK() {
    }

    public List<FlyPoint> loadDataFromPath(String txtPath) {

        List<String> locations = new ArrayList<>();
        ArrayList flyPoints = new ArrayList();
        if (true) {
            File file = new File(txtPath);
            if (file.isDirectory()) {
            } else {
                try {
                    InputStream instream = new FileInputStream(file);
                    if (instream != null) {
                        InputStreamReader inputreader = new InputStreamReader(instream);
                        BufferedReader buffreader = new BufferedReader(inputreader);
                        String line;
                        while ((line = buffreader.readLine()) != null) {
//                            content += line + "\n";
                            if (line.length() > 10) {
                                locations.add(line);
                            }
                        }
                        instream.close();
                    }
                } catch (FileNotFoundException e) {
//                    Log.d("TestFile", "The File doesn't not exist.");
                } catch (IOException e) {
//                    Log.d("TestFile", e.getMessage());
                }
            }

            int num_point = 0;
            for (String location : locations) {
                num_point = num_point + 1;
            }

            //float altitude_0 =FlightControllerState.getTakeoffLocationAltitude();


            //float altitude_read[] = new float[num_point];
            num_point = 1;

            for (String location : locations) {
                String[] datas = location.split(",");
                FlyPoint flyPoint = new FlyPoint();
                flyPoint.setSeq(String.valueOf(num_point));
                flyPoint.setImageName(datas[0].trim());
                flyPoint.setLongitude(datas[1].trim());
                flyPoint.setLatitude(datas[2].trim());
                flyPoint.setElevation(datas[3].trim());
                flyPoint.setFlyHeading(datas[4].trim());
                flyPoint.setVehicleHeading(datas[5].trim());
                flyPoint.setHoveringTime(Integer.parseInt(datas[6].trim()));
                flyPoint.setImageCount(Integer.parseInt(datas[7].trim()));
                flyPoints.add(flyPoint);

                num_point += 1;

//                float longitude = Float.parseFloat(datas[1].trim());
//                float latitude = Float.parseFloat(datas[2].trim());

            }
        }
        return flyPoints;
    }

    //KML 文件生成：
    public void WriteFlyPlanKml(String folderPath, String fileName, List<FlyPoint> flyPoints,int speed) throws
            UnsupportedEncodingException, FileNotFoundException {
        Element root = DocumentHelper.createElement("kml");
        Document document = DocumentHelper.createDocument(root);
        root.addNamespace("", "http://www.opengis.net/kml/2.2");
        Element documentElement = root.addElement("Document");
        documentElement.addAttribute("xmlns", "");
        this.addNoneFolderElements(fileName, documentElement);
        String flyPointsCoordinatesString = "";
        Element folderElement = documentElement.addElement("Folder");
        folderElement.addElement("name").addText("Waypoints");
        folderElement.addElement("description").addText("Waypoints in the Mission.");
        Iterator<FlyPoint> it = flyPoints.iterator();
        FlyPoint flyPoint;
//        接着，通过 addWayPointPlacemark 和 addWayLinePlacemark 方法，向 KML 文件中添加飞行点和路径数据。
        if (it.hasNext()) {
            flyPoint = (FlyPoint) it.next();
            this.addWayPointPlacemark(folderElement, flyPoint,speed);
            flyPointsCoordinatesString = flyPointsCoordinatesString + flyPoint.getLongitude() + "," + flyPoint.getLatitude() + "," + flyPoint.getElevation();
        }

        while (it.hasNext()) {
            flyPoint = (FlyPoint) it.next();
            this.addWayPointPlacemark(folderElement, flyPoint,speed);
            flyPointsCoordinatesString = flyPointsCoordinatesString + " " + flyPoint.getLongitude() + "," + flyPoint.getLatitude() + "," + flyPoint.getElevation();
        }

        this.addWayLinePlacemark(documentElement, flyPointsCoordinatesString);
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");

        XMLWriter xmlWriter = new XMLWriter(new FileOutputStream(folderPath + "/" + fileName + ".kml"), format);

        try {
            xmlWriter.write(document);
        } catch (IOException var12) {
            var12.printStackTrace();
        }

        try {
            xmlWriter.close();
        } catch (IOException var11) {
            var11.printStackTrace();
        }

        //System.out.println("write kml successfully!");
    }

    private void addNoneFolderElements(String fileName, Element documentElement) {
        documentElement.addElement("name").addText(fileName);
        documentElement.addElement("open").addText("1");
        Element extendedDataElement = documentElement.addElement("ExtendedData");
        extendedDataElement.addNamespace("mis", "www.dji.com");
        extendedDataElement.addElement("mis:type").addText("Waypoint");
        extendedDataElement.addElement("mis:stationType").addText("1");
        Element styleElement = documentElement.addElement("Style");
        styleElement.addAttribute("id", "waylineGreenPoly");
        Element wayPointStyleElement = styleElement.addElement("LineStyle");
        wayPointStyleElement.addElement("color").addText("FF0AEE8B");
        wayPointStyleElement.addElement("width").addText("6");
        wayPointStyleElement = documentElement.addElement("Style");
        wayPointStyleElement.addAttribute("id", "waypointStyle");
        wayPointStyleElement.addElement("IconStyle").addElement("Icon").addElement("href").addText("https://cdnen.dji-flighthub.com/static/app/images/point.png");
    }

    private void addWayPointPlacemark(Element folderElement, FlyPoint flyPoint,int speed) {
        Element placemarkElement = folderElement.addElement("Placemark");
        placemarkElement.addElement("name").addText("Waypoint" + flyPoint.getSeq());
        placemarkElement.addElement("visibility").addText("1");
        placemarkElement.addElement("description").addText("Waypoint");
        placemarkElement.addElement("styleUrl").addText("#waypointStyle");
        Element extendedDataElementOfPlaceMark = placemarkElement.addElement("ExtendedData");
        extendedDataElementOfPlaceMark.addNamespace("mis", "www.dji.com");
        extendedDataElementOfPlaceMark.addElement("mis:useWaylineAltitude").addText("true");
        extendedDataElementOfPlaceMark.addElement("mis:heading").addText(flyPoint.getFlyHeading());
        extendedDataElementOfPlaceMark.addElement("mis:turnMode").addText("Auto");
//        extendedDataElementOfPlaceMark.addElement("mis:gimbalPitch").addText(flyPoint.getVehicleHeading());
        extendedDataElementOfPlaceMark.addElement("mis:useWaylineSpeed").addText("true");
        extendedDataElementOfPlaceMark.addElement("mis:speed").addText(String.valueOf(speed));
        extendedDataElementOfPlaceMark.addElement("mis:useWaylineHeadingMode").addText("true");
        extendedDataElementOfPlaceMark.addElement("mis:useWaylinePointType").addText("true");
        extendedDataElementOfPlaceMark.addElement("mis:pointType").addText("LineStop");
        extendedDataElementOfPlaceMark.addElement("mis:cornerRadius").addText("0.2");
        this.addHoveringTime(flyPoint, extendedDataElementOfPlaceMark);

        for (int i = 0; i < flyPoint.getImageCount(); ++i) {
            this.addOneShotPhotoElement(extendedDataElementOfPlaceMark);
        }

        Element pointElementDataElementOfPlaceMark = placemarkElement.addElement("Point");
        pointElementDataElementOfPlaceMark.addElement("altitudeMode").addText("relativeToGround");
        pointElementDataElementOfPlaceMark.addElement("coordinates").addText(flyPoint.getLongitude() + "," + flyPoint.getLatitude() + "," + flyPoint.getElevation());
    }

    private void addOneShotPhotoElement(Element extendedDataElementOfPlaceMark) {
        Element actionElement = extendedDataElementOfPlaceMark.addElement("mis:actions").addText("ShootPhoto");
        actionElement.addAttribute("param", "0");
        actionElement.addAttribute("accuracy", "0");
        actionElement.addAttribute("cameraIndex", "0");
        actionElement.addAttribute("cameraIndex", "0");
        actionElement.addAttribute("payloadIndex", "0");
    }

    private void addHoveringTime(FlyPoint flyPoint, Element extendedDataElementOfPlaceMark) {
        Element actionElement = extendedDataElementOfPlaceMark.addElement("mis:actions").addText("Hovering");
        actionElement.addAttribute("param", String.valueOf(flyPoint.getHoveringTime()));
        actionElement.addAttribute("accuracy", "0");
        actionElement.addAttribute("cameraIndex", "0");
        actionElement.addAttribute("cameraIndex", "0");
        actionElement.addAttribute("payloadIndex", "0");
    }

    private void addWayLinePlacemark(Element documentElement, String
            flyPointsCoordinatesString) {
        Element placemarkElement = documentElement.addElement("Placemark");
        placemarkElement.addElement("name").addText("Wayline");
        placemarkElement.addElement("visibility").addText("1");
        placemarkElement.addElement("description").addText("Wayline");
        this.addWayLineExtendedData(placemarkElement);
        placemarkElement.addElement("styleUrl").addText("#waylineGreenPoly");
        Element lineStringElement = placemarkElement.addElement("LineString");
        lineStringElement.addElement("tessellate").addText("1");
        lineStringElement.addElement("altitudeMode").addText("relativeToGround");
        lineStringElement.addElement("coordinates").addText(flyPointsCoordinatesString);
    }

    private void addWayLineExtendedData(Element placemarkElement) {
        Element extendedDataElementOfPlaceMark = placemarkElement.addElement("ExtendedData");
        extendedDataElementOfPlaceMark.addNamespace("mis", "www.dji.com");
        extendedDataElementOfPlaceMark.addElement("mis:altitude").addText("50.0");
        extendedDataElementOfPlaceMark.addElement("mis:autoFlightSpeed").addText("5.0");
        extendedDataElementOfPlaceMark.addElement("mis:actionOnFinish").addText("GoFirstPoint");
        extendedDataElementOfPlaceMark.addElement("mis:headingMode").addText("UsePointSetting");
        extendedDataElementOfPlaceMark.addElement("mis:gimbalPitchMode").addText("UsePointSetting");
        extendedDataElementOfPlaceMark.addElement("mis:powerSaveMode").addText("false");
        extendedDataElementOfPlaceMark.addElement("mis:waypointType").addText("LineStop");
        Element droneInfoElement = extendedDataElementOfPlaceMark.addElement("mis:droneInfo");
        droneInfoElement.addElement("mis:droneType").addText("COMMON");
        droneInfoElement.addElement("mis:advanceSettings").addText("false");
        droneInfoElement.addElement("mis:droneCameras");
        Element droneHeightElement = droneInfoElement.addElement("mis:droneHeight");
        droneInfoElement.addElement("mis:useAbsolute").addText("false");
        droneInfoElement.addElement("mis:hasTakeoffHeight").addText("false");
        droneInfoElement.addElement("mis:takeoffHeight").addText("0.0");
    }

//    @Value("${kml.path}")
//    private String kmlPath;
//
//    public static void main(String[] args){
//
//        FlyPlan_GBK flyplan = new FlyPlan_GBK();
//        System.out.println(flyplan.kmlPath+"/kml_input.txt");
//        List<FlyPoint> flypoints=flyplan.loadDataFromPath(flyplan.kmlPath+"/kml_input.txt");
//        System.out.print(flypoints);
//        System.out.println("Hello World");
//        try{
//            flyplan.WriteFlyPlanKml(flyplan.kmlPath+"/output","xunjian" , flypoints);
//
//        }catch (UnsupportedEncodingException ue){
//
//        }catch (FileNotFoundException fe){
//
//        }
//    }
}

