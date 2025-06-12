package com.example.baidu_map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;


public class HuaweiIOT {
    private String HUAWEINAME;
    private String IAMINAME;
    private String IAMPASSWORD;
    private String project_id;
    private String device_id;
    private String service_id;
    private String endpoint;
    private String token;

    public HuaweiIOT(String HUAWEINAME, String IAMINAME, String IAMPASSWORD,
                     String project_id, String device_id, String service_id,
                     String endpoint) throws Exception {
        this.HUAWEINAME = HUAWEINAME;
        this.IAMINAME = IAMINAME;
        this.IAMPASSWORD = IAMPASSWORD;
        this.project_id = project_id;
        this.device_id = device_id;
        this.service_id = service_id;
        this.endpoint = endpoint;
        this.token = gettoken();
    }
    /**
    //请在下方完善信息
    String HUAWEINAME="l17738422005";  //华为账号名
    String IAMINAME="test";    //IAM账户名
    String IAMPASSWORD="Lww827717."; //IAM账户密码
    String project_id="67d6be1a375e694aa693300b";  //产品ID
    String device_id="864814071622293";   //设备ID
    String service_id="GPS";  //服务ID
    String commands="";    //命令名称
    //↓可从控制台首页【总览】【接入信息】处获取HTTPS接入地址，例如xxxxxxx.iotda.cn-north-4.myhuaweicloud.com
    String endpoint="b8b855d242.st1.iotda-app.cn-north-4.myhuaweicloud.com";    //终端节点名称

    String token="";//仅作为全局变量使用，无需手动填写
     **/
    public HuaweiIOT()throws Exception//构造函数，自动调用
    {
        token=gettoken();
    }

    //1数据提取
    public String getAtt(String att,String mode) throws Exception{
        String strurl="";
        if(mode=="shadow")  strurl="https://%s/v5/iot/%s/devices/%s/shadow";
        if(mode=="status")  strurl="https://%s/v5/iot/%s/devices/%s";
        strurl = String.format(strurl, endpoint,project_id,device_id);
        URL url = new URL(strurl);
        HttpURLConnection urlCon = (HttpURLConnection)url.openConnection();
        urlCon.addRequestProperty("Content-Type", "application/json");
        urlCon.addRequestProperty("X-Auth-Token",token);
        urlCon.connect();
        InputStreamReader is = new InputStreamReader(urlCon.getInputStream());
        BufferedReader bufferedReader = new BufferedReader(is);
        StringBuffer strBuffer = new StringBuffer();
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            strBuffer.append(line);
        }
        is.close();
        urlCon.disconnect();
        String result = strBuffer.toString();
        System.out.println(result);
        if(mode=="shadow")
        {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readValue(result, JsonNode.class);
            JsonNode tempNode = jsonNode.get("shadow").get(0).get("reported").get("properties").get(att);
            String attvaluestr = tempNode.asText();
            System.out.println(att+"=" + attvaluestr);
            return attvaluestr;
        }
        if(mode=="status")//解析json
        {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readValue(result, JsonNode.class);
            JsonNode statusNode = jsonNode.get("status");
            String statusstr = statusNode.asText();
            System.out.println("status = " + statusstr);
            return statusstr;
        }
        return "error";
    }

    //3获取token
    public String gettoken( )throws Exception
    {
        String strurl="";
        strurl="https://iam.cn-north-4.myhuaweicloud.com"+"/v3/auth/tokens?nocatalog=false";
        String tokenstr="{"+"\""+"auth"+"\""+": {"+"\""+"identity"+"\""+": {"+"\""+"methods"+"\""+": ["+"\""+"password"+"\""+"],"+"\""+"password"+"\""+": {"+"\""+"user"+"\""+":{"+"\""+"domain\": {\"name\": \""+HUAWEINAME+"\"},\"name\": \""+IAMINAME+"\",\"password\": \""+IAMPASSWORD+"\"}}},\"scope\": {\"project\": {\"name\": \"cn-north-4\"}}}}";
        URL url = new URL(strurl);
        HttpURLConnection urlCon = (HttpURLConnection)url.openConnection();
        urlCon.addRequestProperty("Content-Type", "application/json;charset=utf8");
        urlCon.setDoOutput(true);
        urlCon.setRequestMethod("POST");
        urlCon.setUseCaches(false);
        urlCon.setInstanceFollowRedirects(true);
        urlCon.connect();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlCon.getOutputStream(),"UTF-8"));
        writer.write(tokenstr);
        writer.flush();
        writer.close();
        Map headers = urlCon.getHeaderFields();
        Set<String> keys = headers.keySet();
        /*for( String key : keys ){
            String val = urlCon.getHeaderField(key);
            System.out.println(key+"    "+val);
        }*/
        String token = urlCon.getHeaderField("X-Subject-Token");
        System.out.println("X-Subject-Token"+"："+token);
        return  token;
    }


}
