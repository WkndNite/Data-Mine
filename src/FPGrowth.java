import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

class FPTreeNode {
    String item;
    FPTreeNode parent;
    int count;
    Map<String,FPTreeNode> children;
    FPTreeNode next;

    public FPTreeNode(String item,FPTreeNode parent) {
        this.item = item;
        this.parent = parent;
        this.count = 1;
        this.children = new HashMap<>();
        this.next = null;
    }

    public void increment(){
        this.count++;
    }
}

public class FPGrowth {
    public static void main(String[] args) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String filename = "result-" + timestamp + ".txt"; // 带时间戳的文件名

        String filePath1 = "src/chainstoreFIM.txt";
        String filePath2 = "src/OnlineRetailZZ.txt";

        List<List<String>> transactions1 = loadTransactions(filePath1);
        List<List<String>> transactions2 = loadTransactions(filePath2);

        int[] minSupports = {6000,8000,10000,12000}; // 支持度从高到低

        for (int minSupport : minSupports) {
            runFPGrowth("chainstoreFIM", transactions1, minSupport,filename);
        }

        for (int minSupport : minSupports) {
            runFPGrowth("OnlineRetailZZ", transactions2, minSupport,filename);
        }
    }
    private static List<List<String>> loadTransactions(String filePath) throws IOException{
        List<List<String>> transactions = new ArrayList<>();
        String path = Paths.get(filePath).toAbsolutePath().toString();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        while ((line = br.readLine()) != null) {
            String[] items = line.split(" ");
            List<String> transaction = new ArrayList<>();
            for(String item : items) {
                if(!item.trim().isEmpty()) {
                    transaction.add(item);
                }
            }
            transactions.add(transaction);
        }
        br.close();
        return transactions;
    }

    private static Map<String,Integer> countFrequencies(List<List<String>> transactions) {
        Map<String,Integer> frequencies = new HashMap<>();
        for(List<String> transaction : transactions) {
            for(String item : transaction) {
                frequencies.put(item, frequencies.getOrDefault(item, 0) + 1);
            }
        }
        return frequencies;
    }

    private static Set<String> filterBySupport(Map<String,Integer> frequencies,int support) {
        Set<String> frequentItems = new HashSet<>();
        for(Map.Entry<String,Integer> entry : frequencies.entrySet()) {
            if(entry.getValue() >= support) {
                frequentItems.add(entry.getKey());
            }
        }
        return frequentItems;
    }

    private static List<List<String>> sortTransactions(List<List<String>> transactions,
                                                       Map<String, Integer> frequency,
                                                       Set<String> frequentItems) {
        List<List<String>> sorted = new ArrayList<>();
        for (List<String> transaction : transactions) {
            List<String> filtered = new ArrayList<>();
            for (String item : transaction) {
                if (frequentItems.contains(item)) {
                    filtered.add(item);
                }
            }
            filtered.sort((a, b) -> frequency.get(b) - frequency.get(a)); // 按频率降序
            if (!filtered.isEmpty()) {
                sorted.add(filtered);
            }
        }
        return sorted;
    }

    private static FPTreeNode buildTree(List<List<String>> transactions, Map<String, FPTreeNode> headerTable){
        FPTreeNode root = new FPTreeNode(null,null);
        for(List<String> transaction : transactions) {
            insertTree(transaction, root, headerTable);
        }
        return root;
    }

    private static void insertTree(List<String> transaction, FPTreeNode node, Map<String, FPTreeNode> headerTable){
        if(transaction.isEmpty()) return;

        String firstItem = transaction.getFirst();
        FPTreeNode child = node.children.get(firstItem);
        if(child == null) {
            child = new FPTreeNode(firstItem,node);  // 注意这里 parent 应该是 node！
            node.children.put(firstItem,child);

            if (headerTable.containsKey(firstItem)) {
                FPTreeNode current = headerTable.get(firstItem);
                while (current.next != null) {
                    current = current.next;
                }
                current.next = child;
            } else {
                headerTable.put(firstItem, child);
            }
        }else {
            child.increment();
        }
        insertTree(transaction.subList(1,transaction.size()), child, headerTable);
    }



    private static void printTree(FPTreeNode node, int level) {
        if (node.item != null) {
            System.out.println("  ".repeat(level) + node.item + " (" + node.count + ")");
        }
        for (FPTreeNode child : node.children.values()) {
            printTree(child, level + 1);
        }
    }

    private static void mineTree(FPTreeNode root,Map<String,FPTreeNode> headerTable,int minSupport,List<String> prefix,List<List<String>> frequentPatterns){
        System.out.println("正在挖掘"+root);
        // 频率升序访问
        List<String> items = new ArrayList<>(headerTable.keySet());
        items.sort(Comparator.comparingInt(item -> {
            int sum = 0;
            FPTreeNode node = headerTable.get(item);
            while (node != null) {
                sum += node.count;
                node = node.next;
            }
            return sum;
        }));

        for (String item : items) {
            List<String> newPattern = new ArrayList<>(prefix);
            newPattern.add(item);
            frequentPatterns.add(newPattern); // 添加一个频繁项集

            // 构建条件模式基
            List<List<String>> conditionalPatternBase = new ArrayList<>();
            FPTreeNode node = headerTable.get(item);
            while (node != null) {
                List<String> path = new ArrayList<>();
                FPTreeNode parent = node.parent;
                while (parent != null && parent.item != null) {
                    path.add(parent.item);
                    parent = parent.parent;
                }
                for (int i = 0; i < node.count; i++) {
                    if (!path.isEmpty()) {
                        conditionalPatternBase.add(new ArrayList<>(path));
                    }
                }
                node = node.next;
            }

            // 构建条件 FP 树
            Map<String, Integer> condFrequencies = countFrequencies(conditionalPatternBase);
            Set<String> condFrequentItems = filterBySupport(condFrequencies, minSupport);
            List<List<String>> condSorted = sortTransactions(conditionalPatternBase, condFrequencies, condFrequentItems);

            Map<String, FPTreeNode> newHeader = new HashMap<>();
            FPTreeNode newRoot = buildTree(condSorted, newHeader);

            // 递归挖掘条件 FP 树
            if (!newHeader.isEmpty()) {
                mineTree(newRoot, newHeader, minSupport, newPattern, frequentPatterns);
            }
        }
    }

    private static void writeToFile(String fileName, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(content);
        } catch (IOException e) {
            System.out.println("Error writing to file: " + fileName);
        }
    }
    private static void runFPGrowth(String datasetName, List<List<String>> transactions, int minSupport,String filename) {
        // 强制进行垃圾回收，以减少 GC 可能带来的影响
        System.gc();

        // 记录执行开始时间
        long startTime = System.nanoTime();

        // 记录开始时的内存使用情况
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // 初始化 Header 表
        Map<String, FPTreeNode> headerTable = new HashMap<>();

        // 计算频率
        Map<String, Integer> frequencies = countFrequencies(transactions);

        // 根据最小支持度筛选频繁项
        Set<String> frequentItems = filterBySupport(frequencies, minSupport);

        // 排序交易
        List<List<String>> sortedTransactions = sortTransactions(transactions, frequencies, frequentItems);

        // 构建 FP 树
        FPTreeNode root = buildTree(sortedTransactions, headerTable);

        // 挖掘频繁项集
        List<List<String>> frequentPatterns = new ArrayList<>();
        mineTree(root, headerTable, minSupport, new ArrayList<>(), frequentPatterns);

        // 记录执行结束时间
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000; // 转换为毫秒

        // 记录结束时的内存使用情况
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = endMemory - startMemory;

        // 确保内存使用量不为负值
        if (memoryUsed < 0) {
            memoryUsed = 0;
        }

        // 准备输出的结果字符串
        String result = "==== 数据集：" + datasetName + " | 最小支持度：" + minSupport + " ====\n";
        result += "频繁项集个数：" + frequentPatterns.size() + "\n";
        result += "执行时间： " + duration + " 毫秒\n";
        result += "内存使用： " + memoryUsed / 1024 + " KB\n";

        // 打印到控制台
        System.out.println(result);

        for (List<String> pattern : frequentPatterns) {
            System.out.println(pattern);
        }
        result += "\n";

        // 将结果写入到文件中，确保以追加模式写入
        writeToFile(filename, result);
    }



}
