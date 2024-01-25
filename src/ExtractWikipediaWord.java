
/*
 * Wikipediaのダンプファイルから「読み」と「表記」の組み合わせを抽出する。
 * 
 * https://dumps.wikimedia.org/jawiki/latest/jawiki-latest-pages-articles.xml.bz2
 * を展開して./data/ディレクトリに置き、実行する。
 * 
 *「の一覧」のような文字列の内容による取捨選択はここではおこなわない。
 * カタカナ語を別扱いできるように「読み」のカタカナはひらがなに変換していない。
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.TreeSet;

public class ExtractWikipediaWord {

    static Set<String> setWord = new TreeSet<>(); // 表記でソート、重複なし
    static BufferedWriter bwTest;

    static boolean isReadingFirstChar(char ch) {
        if (ch >= 'ァ' && ch <= 'ヶ') {
            return true;
        }
        if (ch >= 'ぁ' && ch <= 'ゖ') {
            return true;
        }
        return false;
    }

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

    // 全角カタカナ変換
    public static char toWideKatakana(char ch) {
        if (ch >= 'ぁ' && ch <= 'ゖ') {
            return (char) (ch - 'ぁ' + 'ァ');
        }
        return ch;
    }

    // 全角カタカナ変換
    public static String toWideKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideKatakana(cs.charAt(i)));
        }
        return sb.toString();
    }

    // 複数の語句がある場合の区切りに"・"が使われているので"・"は読みや表記に含められない
    static void addWord(String reading, String surface) throws IOException {
        reading = reading.strip();
        surface = surface.strip();

        if (reading.length() == 0 || surface.length() == 0) {
            return;
        }
        if (!reading.matches("^[ぁ-ゖァ-ヶー]+$")) {
            return;
        }

        if (surface.indexOf('・') >= 0) {
            return;
        }
        if (!surface.matches("^[\\p{InHiragana}\\p{InKatakana}\\p{InCJKunifiedideographs}々]+$")) {
            return;
        }

        // 表記が漢字以外を含む場合
        if (!surface.matches("^[\\p{InCJKunifiedideographs}々]+$")) {
            // 表記のかなが読みにすべて含まれているか
            String surfacePattern = surface.replaceAll("[\\p{InCJKunifiedideographs}々ゝゞ]+", ".*");

            // 一旦カタカナにして正規表現に書き換える
            surfacePattern = toWideKatakana(surfacePattern);

            surfacePattern = surfacePattern.replace("ッ", "[っ|つ]");
            surfacePattern = surfacePattern.replace("ツ", "[っ|つ]");
            surfacePattern = surfacePattern.replace("ヤ", "[や|ゃ]");
            surfacePattern = surfacePattern.replace("ヵ", "[か|が]");
            surfacePattern = surfacePattern.replace("ヶ", "[ゖ|け|か|が]");
            surfacePattern = surfacePattern.replace("ケ", "[ゖ|け|か|が]");
            surfacePattern = surfacePattern.replace("ヰ", "[ゐ|い]");
            surfacePattern = surfacePattern.replace("ヱ", "[ゑ|え]");
            surfacePattern = surfacePattern.replace("ヲ", "[を|お]");

            // 書き換えたものを全てひらがなにする
            surfacePattern = toWideHiragana(surfacePattern);

            String readingHiragana = toWideHiragana(reading);
            if (!readingHiragana.matches(surfacePattern)) {
                return;
            }
        }

        // 登録
        setWord.add(surface + "\t" + reading);
    }

    static void parseLine(String line) throws IOException {

        String[] ss;
        int index;
        // 「～'''（～」に対する処理
        ss = line.split("'''（");
        if (ss.length >= 2) {
            for (int i = 0; i < ss.length - 1; i++) {
                String left = ss[i].strip();
                String right = ss[i + 1].strip();
                if (left.length() == 0 || right.length() == 0) {
                    continue;
                }

                if (!isReadingFirstChar(right.charAt(0))) {
                    continue;
                }
                index = left.lastIndexOf("'''");
                if (index < 0) {
                    continue;
                }
                // 表記
                String surface = left.substring(index + 3);
                // 読み
                index = right.indexOf('）');
                if (index < 0) {
                    continue;
                }
                right = right.substring(0, index).strip();
                // 複数の読みがあれば分割
                String[] readings = right.split("、|,|／|/");
                for (String reading : readings) {
                    addWord(reading, surface);
                }
            }
        }
        // 「{{読み仮名～}}」に対する処理
        ss = line.split("\\{\\{読み仮名[^|]*\\|");
        if (ss.length >= 2) {
            for (int i = 0; i < ss.length - 1; i++) {
                String[] ss2 = ss[i + 1].split("'''\\|");
                if (ss2.length < 2) {
                    continue;
                }
                String left = ss2[0].strip();
                String right = ss2[1].strip();

                if (!isReadingFirstChar(right.charAt(0))) {
                    continue;
                }
                index = left.lastIndexOf("'''");
                if (index < 0) {
                    continue;
                }
                // 表記
                String surface = left.substring(index + 3);
                // 読み
                index = right.indexOf('|');
                if (index < 0) {
                    continue;
                }
                right = right.substring(0, index).strip();
                index = right.indexOf('}');
                if (index >= 0) {
                    right = right.substring(0, index).strip();
                }
                index = right.indexOf('&');
                if (index >= 0) {
                    right = right.substring(0, index).strip();
                }
                index = right.indexOf('、');
                if (index >= 0) {
                    right = right.substring(0, index).strip();
                }
                index = right.indexOf('{');
                if (index >= 0) {
                    right = right.substring(0, index).strip();
                }
                addWord(right, surface);
            }
        }
    }

    static public void main(String argv[]) throws Exception {

        // 動作確認出力用
        bwTest = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                new File("test.txt")), "UTF-8"));

        // ダンプファイル読み出し用
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File("data/jawiki-latest-pages-articles.xml")), "UTF-8"));

        String line;
        while ((line = br.readLine()) != null) {
            line = line.strip();
            if (line.length() == 0) {
                continue;
            }
            // 同じ行に混在する場合があるのでどちらかに一致すればまとめて処理する
            if (line.contains("'''（") || line.contains("{{読み仮名")) {
                parseLine(line);
            }
        }

        br.close();

        bwTest.flush();
        bwTest.close();

        BufferedWriter bwWord = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("WikipediaWord.txt")), "UTF-8"));
        for (String set : setWord) {
            bwWord.write(set + "\n");
        }
        bwWord.flush();
        bwWord.close();

    }
}
