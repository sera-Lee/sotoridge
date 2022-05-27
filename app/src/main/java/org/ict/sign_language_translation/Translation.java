package org.ict.sign_language_translation;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

class WiseNLU {
    static public class Morpheme {
        final String text;
        final String type;
        Integer count;

        public Morpheme (String text, String type, Integer count) {
            this.text = text;
            this.type = type;
            this.count = count;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    static public void main(String[] args) {
        // 언어 분석 기술(문어)
        String openApiURL = "http://aiopen.etri.re.kr:8000/WiseNLU";
        String accessKey = " 5fe3e0e6-8c3d-458f-b415-2ef6d1a1cc65"; // 발급받은 API Key
        String analysisCode = "morp"; // 형태소 분석 코드
        String text = ""; // 분석할 텍스트 데이터 (OCR로부터 받아옴)
        Gson gson = new Gson();

        // 언어 분석 기술(문어)
        text += "실습실 주의 사항" +
                "1. USB 바이러스 감염 주의" +
                "(USB 사용 시 포맷하시고 사용하시기 바랍니다.)" +
                "2. 컴퓨터 종료 후 퇴실하시기 바랍니다." +
                "3. 강의실 내 비품 및 소모품 절도 행위를 금지합니다." +
                "(CCTV 녹화 중)";

        Map<String, Object> request = new HashMap<>();
        Map<String, String> argument = new HashMap<>();

        argument.put("analysis_code", analysisCode);
        argument.put("text", text);

        request.put("access_key", accessKey);
        request.put("argument", argument);

        URL url;
        Integer responseCode = null;
        String responBodyJson = null;
        Map<String, Object> responeBody = null;

        try {
            url = new URL(openApiURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.write(gson.toJson(request).getBytes("UTF-8"));
            wr.flush();
            wr.close();

            responseCode = con.getResponseCode();
            InputStream is = con.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuffer sb = new StringBuffer();

            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            responBodyJson = sb.toString();

            // http 요청 오류 시 처리
            if ( responseCode != 200 ) {
                // 오류 내용 출력
                System.out.println("[error] " + responBodyJson);
                return ;
            }

            responeBody = gson.fromJson(responBodyJson, Map.class);
            Integer result = ((Double) responeBody.get("result")).intValue();
            Map<String, Object> returnObject;
            List<Map> sentences;

            // 분석 요청 오류 시 처리
            if ( result != 0 ) {

                // 오류 내용 출력
                System.out.println("[error] " + responeBody.get("result"));
                return ;
            }

            // 분석 결과 활용
            returnObject = (Map<String, Object>) responeBody.get("return_object");
            sentences = (List<Map>) returnObject.get("sentence");

            Map<String, Morpheme> morphemesMap = new HashMap<String, Morpheme>();
            List<Morpheme> morphemes = null;

            for( Map<String, Object> sentence : sentences ) {

                // 형태소 분석기 결과 수집 및 정렬
                List<Map<String, Object>> morphologicalAnalysisResult = (List<Map<String, Object>>) sentence.get("morp");

                for (Map<String, Object> morphemeInfo : morphologicalAnalysisResult) {
                    String lemma = (String) morphemeInfo.get("lemma");
                    Morpheme morpheme = morphemesMap.get(lemma);

                    if (morpheme == null) {
                        morpheme = new Morpheme(lemma, (String) morphemeInfo.get("type"), 1);
                        morphemesMap.put(lemma, morpheme);
                    } else {
                        morpheme.count = morpheme.count + 1;
                    }
                }

                if (0 < morphemesMap.size()) {
                    morphemes = new ArrayList<Morpheme>(morphemesMap.values());
                    translate();
                    morphemes.sort((morpheme1, morpheme2) -> {
                        return morpheme2.count - morpheme1.count;
                    });
                }
            }

            morphemes
                    .stream()
                    .forEach(morpheme -> {
                        Log.d("{ " + morpheme.text + " : " + morpheme.type + " }\n", "test");
                    });


            /*
            // 형태소들 중 명사들에 대해서 많이 노출된 순으로 출력 ( 최대 5개 )
            morphemes
                    .stream()
                    .filter(morpheme -> {
                        return morpheme.type.equals("NNG") ||
                                morpheme.type.equals("NNP") ||
                                morpheme.type.equals("NNB");
                    })
                    .limit(5)
                    .forEach(morpheme -> {
                        Log.d("[명사] " + morpheme.text + " ("+morpheme.count+")", "확인");
                    });
             */

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String translate (List<Map<String, String>> input) {
        String output = "";


        for (int i = 0 ; i < input.size() ; i++) {

            // 1. 수화 표현을 위한 문장 요소 제거
            if ((input.get(i).containsKey("XSV"))) { // 동사 파생 접미사
                input.remove(i);
            }
            else if ((input.get(i).containsKey("EP"))) { // 선어말 어미
                input.remove(i);
            }
            else if ((input.get(i).containsKey("ETN"))) { // 명사형 전성 어미
                input.remove(i);
            }
            else if ((input.get(i).containsKey("EF"))) { // 종결 어미
                input.remove(i);
            }

        }


        /*
         실습실 주의 사항
         1. USB 바이러스 감염 주의 (USB 사용 시 포맷하시고 사용하시기 바랍니다.)
         2. 컴퓨터 종료 후 퇴실하기 바랍니다.
         3. 강의실 내 비품 및 소모품 절도 행위를 금지합니다. (CCTV 녹화 중)
         */

        /* JSONObject jsonObject = new JSONObject(json);
           JSONArray Array = jsonObject.getJSONArray("sentence").getJSONArray("WSD");

        for(int i=0; i<Array.length(); i++) {
                JSONObject object = Array.getJSONObject(i);

                // 1. 수화 표현을 위한 문장 요소 제거
                // 조사(J~) / 어미(E~) / 문법적 요소(S~)
                if (!(object.getString("type").start    sWith("J") && object.getString("type").startsWith("E")
                && object.getString("type").startsWith("S"))) {

                // 문법적 요소(SF)에서는 물음표를 얻어내야 함
                }

                // 2. 수화 표현의 변환 및 시제 표현
                if (object.getString("type").equals("NNB")) {
                    if (
                    // 의존 명사는 다 NNB로 취급이 되어서 단위성 의존 명사(마리, 명, 그루, 개 등)을 찾아내서 어떻게 제거하는지?
                    // 숫자(SN), 수사(NR) 태그 뒤에 NNB가 오는 경우 제거

                    // 시제를 나타내는 어미가 선어말 어미(EP)에도 포함되어 있는데 어떻게 구분하는지?
                    // EP를 따로 검사하여서 었, 였과 같은 시제 표현을 발견하면 '끝'이라는 수어적 표현으로 변환되게
                }

               // 3. 수화 높임말 용어 변경 및 위치 이동
                    // 높임말은 표현이 한정적이므로 직접 변환? (계시다 -> 있다, 잡수시다 -> 먹다)
                    // '시'와 같은 높임을 나타내는 선어말 어미는 EP 태그로 따로 분류되어 걸러질 것으로 예상
            }
         */

        return output;
    }
}
