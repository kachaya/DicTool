import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.TreeSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikipediaYomigana {

    static Set<String> setDict = new TreeSet<>();

    // 全角ひらがな変換
    public static char toWideHiragana(char ch) {
        if (ch >= 'ァ' && ch <= 'ヶ') {
            return (char) (ch - 'ァ' + 'ぁ');
        }
        return ch;
    }

    // 全角ひらがな変換
    public static String toWideHiragana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideHiragana(cs.charAt(i)));
        }
        return sb.toString();
    }

    static void processYomiganaLine(String line) {
        line = line.replace("'''", "");
        line = line.replace("''", "");

        Pattern p = Pattern.compile("\\{\\{読み仮名[^\\}\\}]*\\}\\}");
        Matcher m = p.matcher(line);
        String reading;
        String surface;
        while (m.find()) {
            String s = m.group().strip();
            int pos = s.indexOf("|");
            if (pos < 0) {
                continue;
            }
            s = s.substring(pos + 1).strip();
            char ch = s.charAt(0);
            if (ch == '&') {
                continue;
            }
            if (ch == '{') {
                continue;
            }

            // "[[]]"の中に'|'がある場合を考慮
            int startPos = s.indexOf("[[");
            int endPos = s.indexOf("]]");

            if (startPos < 0 && endPos < 0) {
                // [[]]で囲まれていない
                int pos1 = s.indexOf("|");
                if (pos1 < 0) {
                    continue;
                }
                surface = s.substring(0, pos1).strip();
                reading = s.substring(pos1 + 1).strip();
            } else if (startPos == 0 && endPos > 2) {
                // 表記が[[]]で囲まれている
                String surfaces = s.substring(startPos + 2, endPos);
                pos = surfaces.indexOf("|");
                if (pos < 0) {
                    surface = surfaces;
                } else {
                    surface = surfaces.substring(pos + 1);
                }
                s = surface + s.substring(endPos + 2);
                int pos1 = s.indexOf("|");
                if (pos1 < 0) {
                    continue;
                }
                surface = s.substring(0, pos1).strip();
                reading = s.substring(pos1 + 1).strip();
            } else {
                continue;
            }
            // 表記チェック
            if (surface.length() == 0) {
                continue;
            }
            if (!surface
                    .matches("^[\\p{InHiragana}\\p{InKatakana}\\p{InCJKunifiedideographs}々]+$")) {
                continue;
            }
            if (surface.contains("・")) {
                continue;
            }
            // 読みチェック
            if (reading.contains("{{く}}") || reading.contains("{{ぐ}}")) {
                continue;
            }
            // 末尾の調整
            int index = reading.indexOf("|");
            if (index >= 0) {
                reading = reading.substring(0, index);
            }
            index = reading.indexOf("{");
            if (index >= 0) {
                reading = reading.substring(0, index);
            }
            index = reading.indexOf("}");
            if (index >= 0) {
                reading = reading.substring(0, index);
            }
            index = reading.indexOf("&");
            if (index >= 0) {
                reading = reading.substring(0, index);
            }
            index = reading.indexOf("、");
            if (index >= 0) {
                reading = reading.substring(0, index);
            }

            reading = toWideHiragana(reading);
            if (!reading.matches("^[ぁ-ゖー]+$")) {
                continue;
            }
            // 読みが一文字以下
            if (reading.length() <= 1) {
                continue;
            }
            // 読みより表記のほうが長い
            if (surface.length() > reading.length()) {
                continue;
            }
            // 登録
            setDict.add(reading + "\t" + surface);
        }
    }

    static public void main(String argv[]) throws Exception {

        BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("data/jawiki-latest-pages-articles.xml")), "UTF-8"));

        String line;
        while ((line = br.readLine()) != null) {
            line = line.strip();
            if (line.startsWith("&lt;!--")) {
                continue;
            }
            if (line.contains("{{読み仮名")) {
                processYomiganaLine(line);
            }
        }
        br.close();

        BufferedWriter bwDict = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("WikipediaYomigana.txt")), "UTF-8"));
        for (String set : setDict) {
            bwDict.write(set + "\n");
        }
        bwDict.flush();
        bwDict.close();
    }
}
