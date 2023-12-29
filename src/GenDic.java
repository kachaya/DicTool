import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.Character.UnicodeBlock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;

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

    static Set<String> listAll = new TreeSet<>();
    static ArrayList<String> listSkip = new ArrayList<>();
    static ArrayList<String> listLex = new ArrayList<>();
    static ArrayList<String> listSymbol = new ArrayList<>();
    static Set<String> setComplement = new LinkedHashSet<>();

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
            // 固有名詞-人名-名などのcostが10000のものは他の候補に比べておかしい
            if (cost <= 0 || cost == 10000) {
                cost = 20000;
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

            if (data[5].equals("記号") || data[5].equals("補助記号")) {
                if (!reading.equals("きごう")) {
                    listAll.add(entry);
                    continue;
                }
                UnicodeBlock block = UnicodeBlock.of(surface.charAt(0));
                if (block.equals(UnicodeBlock.HIGH_SURROGATES)) {
                    listAll.add(entry);
                    continue;
                }
                listSymbol.add(block.toString() + "\t" + entry + "\t" + line);
                // System.err.println(line + " : " + block.toString());
            }

            // 表記にかな漢字以外が含まれているものはスキップ
            if (!surface
                    .matches("^[\\p{InHiragana}\\p{InKatakana}\\p{InCJKunifiedideographs}]+$")) {
                listSkip.add(entry);
                continue;
            }

            // 採用した*_lex.csv内の行
            listLex.add(line);

            // 語尾の補完

            // 「来」
            if (data[9].contains("カ行変格")) {
                listAll.add(entry);
                switch (data[10]) {
                    case "連用形-一般": // 「き」
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        entry = reading + "て\t" + cost + "\t" + surface + "て";
                        listAll.add(entry);
                        continue;
                    case "未然形-一般": // 「こ」
                        entry = reading + "ない\t" + cost + "\t" + surface + "ない";
                        listAll.add(entry);
                        entry = reading + "ず\t" + cost + "\t" + surface + "ず";
                        listAll.add(entry);
                        continue;
                }
                continue;
            }
            if (data[9].contains("サ行変格")) {
                listAll.add(entry);
                switch (data[10]) {
                    case "連用形-一般":
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        entry = reading + "て\t" + cost + "\t" + surface + "て";
                        listAll.add(entry);
                        continue;
                }
                // setComplement.add(line);
                continue;
            }
            if (reading.endsWith("っ") && surface.endsWith("っ")) {
                switch (data[5]) {
                    case "名詞":
                    case "代名詞":
                    case "接頭辞":
                    case "形状詞":
                        listSkip.add(entry);
                        continue;
                    case "動詞":
                        switch (data[10]) {
                            case "意志推量形":
                                listAll.add(entry);
                                entry = reading + "と\t" + cost + "\t" + surface + "と";
                                listAll.add(entry);
                                break;
                            case "連用形-促音便":
                                entry = reading + "た\t" + cost + "\t" + surface + "た";
                                listAll.add(entry);
                                entry = reading + "て\t" + cost + "\t" + surface + "て";
                                listAll.add(entry);
                                break;
                            default:
                                listSkip.add(entry);
                                // System.out.println(line);
                                break;
                        }
                        continue;
                    case "助動詞":
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        entry = reading + "て\t" + cost + "\t" + surface + "て";
                        listAll.add(entry);
                        continue;
                    case "形容詞":
                        entry = reading + "た\t" + cost + "\t" + surface + "た";
                        listAll.add(entry);
                        continue;
                    case "接尾辞":
                        switch (data[6]) {
                            case "動詞的":
                                entry = reading + "て\t" + cost + "\t" + surface + "て";
                                listAll.add(entry);
                                entry = reading + "た\t" + cost + "\t" + surface + "た";
                                listAll.add(entry);
                                break;
                            case "形容詞的":
                                entry = reading + "た\t" + cost + "\t" + surface + "た";
                                listAll.add(entry);
                                break;
                            case "名詞的":
                                listAll.add(entry);
                                break;
                            default:
                                listSkip.add(entry);
                                break;
                        }
                        continue;
                    case "副詞":
                        listAll.add(entry);
                        entry = reading + "と\t" + cost + "\t" + surface + "と";
                        listAll.add(entry);
                        continue;
                    case "感動詞":
                        listAll.add(entry);
                        continue;
                    default:
                        break;
                }
                setComplement.add(line);
                continue;
            }
            if (data[10].equals("連用形-一般")) {
                // 「見た」等
                if (data[9].contains("上一段")) {
                    listAll.add(entry);
                    entry = reading + "よう\t" + cost + "\t" + surface + "よう";
                    listAll.add(entry);
                    entry = reading + "ない\t" + cost + "\t" + surface + "ない";
                    listAll.add(entry);
                    entry = reading + "る\t" + cost + "\t" + surface + "る";
                    listAll.add(entry);
                    entry = reading + "た\t" + cost + "\t" + surface + "た";
                    listAll.add(entry);
                    entry = reading + "れ\t" + cost + "\t" + surface + "れ";
                    listAll.add(entry);
                    entry = reading + "ろ\t" + cost + "\t" + surface + "ろ";
                    listAll.add(entry);
                    continue;
                }
                // 「得た」等
                if (data[9].contains("下一段")) {
                    listAll.add(entry);
                    entry = reading + "ない\t" + cost + "\t" + surface + "ない";
                    listAll.add(entry);
                    entry = reading + "ぬ\t" + cost + "\t" + surface + "ぬ";
                    listAll.add(entry);
                    entry = reading + "ず\t" + cost + "\t" + surface + "ず";
                    listAll.add(entry);
                    entry = reading + "た\t" + cost + "\t" + surface + "た";
                    listAll.add(entry);
                    entry = reading + "て\t" + cost + "\t" + surface + "て";
                    listAll.add(entry);
                    continue;
                }

                continue;
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

        BufferedWriter bwComplement = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("complement.csv")), "UTF-8"));
        for (String list : setComplement) {
            bwComplement.write(list + "\n");
        }
        bwComplement.close();

        listLex.sort(Comparator.naturalOrder());
        BufferedWriter bwLex = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("lex.csv")), "UTF-8"));
        for (String list : listLex) {
            bwLex.write(list + "\n");
        }
        bwLex.close();

        listSymbol.sort(Comparator.naturalOrder());
        BufferedWriter bwSymbol = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("symbol.txt")), "UTF-8"));
        for (String list : listSymbol) {
            bwSymbol.write(list + "\n");
        }
        bwSymbol.close();

        listSkip.sort(Comparator.naturalOrder());
        BufferedWriter bwSkip = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File("skip.txt")), "UTF-8"));
        for (String list : listSkip) {
            bwSkip.write(list + "\n");
        }
        bwSkip.close();

        // listAll.sort(Comparator.naturalOrder());

        File f = new File(SYS_DIC_NAME + ".txt");
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        BufferedWriter bw = new BufferedWriter(osw);

        Files.delete(Paths.get(SYS_DIC_NAME + ".db"));
        Files.delete(Paths.get(SYS_DIC_NAME + ".lg"));

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
