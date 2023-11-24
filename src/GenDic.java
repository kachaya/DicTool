import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;

public class GenDic {
    static String SYS_DIC_NAME = "system_dic";
    static String BTREE_NAME = "btree_dic";

    static String unescape(String s) {
        s = s.replace("\\u0022", "\"");
        s = s.replace("\\u0028", "(");
        s = s.replace("\\u0029", ")");
        s = s.replace("\\u002C", ",");
        s = s.replace("\\u002c", ",");
        s = s.replace("\\u002F", "/");
        s = s.replace("\\u002f", "/");
        s = s.replace("\\u007C", "|");
        s = s.replace("\\u007c", "|");
        return s;
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

    static ArrayList<String> listAll = new ArrayList<>();
    static ArrayList<String> listSkip = new ArrayList<>();

    static void readLex(String filename) throws IOException {
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            String reading = toWideHiragana(unescape(data[11]));
            int cost = Integer.parseInt(data[3]);
            if (cost <= 0) {
                cost = 10000;
            }
            cost += 32768; // 文字列としてソートするため5桁にする
            String surface = unescape(data[4]);
            String entry = reading + "\t" + cost + "\t" + surface;

            // アスキーアートはスキップ
            if (data[6].equals("ＡＡ")) {
                listSkip.add(entry);
                continue;
            }
            // 分割タイプがCの名詞はスキップ
            if (data[5].equals("名詞") && data[14].equals("C")) {
                listSkip.add(entry);
                continue;
            }
            // 読みが平仮名以外の文字を含むものはスキップ
            if (!reading.matches("^[ぁ-ゖー]+$")) {
                listSkip.add(entry);
                continue;
            }
            // 表記にかな漢字以外が含まれているものはスキップ
            if (!surface
                    .matches("^[\\p{InHiragana}\\p{InKatakana}\\p{InCJKunifiedideographs}]+$")) {
                listSkip.add(entry);
                continue;
            }

            if (data[10].endsWith("-促音便")) {
                if (data[5].equals("形容詞")) {
                    entry = reading + "た\t" + cost + "\t" + surface + "た";
                    listAll.add(entry);
                    continue;
                }
                if (data[5].equals("動詞") || data[5].equals("助動詞")) {
                    entry = reading + "た\t" + cost + "\t" + surface + "た";
                    listAll.add(entry);
                    entry = reading + "て\t" + cost + "\t" + surface + "て";
                    listAll.add(entry);
                    continue;
                }
                if (data[5].equals("接尾辞")) {
                    if (data[6].equals("形容詞的")) {
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        continue;
                    }
                    if (data[6].equals("動詞的")) {
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        entry = reading + "て\t" + cost + "\t" + surface + "て";
                        listAll.add(entry);
                        continue;
                    }
                }
                // System.err.println(line);
            }
            listAll.add(entry);
        }
        br.close();
    }

    static public void main(String argv[]) throws Exception {

        RecordManager recman;
        BTree tree;
        Properties props;

        props = new Properties();

        readLex("./data/small_lex.csv");
        readLex("./data/core_lex.csv");
        readLex("./data/notcore_lex.csv");

        listSkip.sort(Comparator.naturalOrder());
        BufferedWriter bwSkip = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("skip.txt")), "UTF-8"));
        for (String list : listSkip) {
            bwSkip.write(list + "\n");
        }
        bwSkip.close();

        listAll.sort(Comparator.naturalOrder());

        File f = new File(SYS_DIC_NAME + ".txt");
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        BufferedWriter bw = new BufferedWriter(osw);

        recman = RecordManagerFactory.createRecordManager(SYS_DIC_NAME, props);
        tree = BTree.createInstance(recman, new StringComparator());
        recman.setNamedObject(BTREE_NAME, tree.getRecid());
        System.out.println("Created a new empty BTree");

        String key = "";
        String value = "";
        for (String list : listAll) {
            String ss[] = list.split("\t");
            String reading = ss[0];
            String surface = ss[2];
            if (reading.equals(key)) {
                if (!value.contains(surface)) {
                    value = value + "\t" + surface;
                }
            } else {
                if (value.length() != 0) {
                    bw.write(key + "\t" + value + "\n");
                    tree.insert(key, value, true);
                }
                key = reading;
                value = surface;
            }
        }
        if (value.length() != 0) {
            bw.write(key + "\t" + value + "\n");
            tree.insert(key, value, true);
        }
        recman.commit();
        recman.close();
        bw.close();
    }
}
