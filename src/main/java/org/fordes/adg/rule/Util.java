package org.fordes.adg.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.enums.RuleType;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.hutool.core.thread.ThreadUtil.sleep;

/**
 * @author DD-AD on 2022/9/19
 */
@Slf4j
public class Util {

    static final Lock lock = new ReentrantLock();

    static int count = 0;

    public static void writeToStream(BufferedOutputStream out, Collection<String> content, String ruleUrl) {
        if (lock.tryLock()) {
            try {
                out.write(StrUtil.format(Constant.COMMENT_TEMPLATE, ruleUrl).getBytes(StandardCharsets.UTF_8));
                for (String line : content) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.write(StrUtil.CRLF.getBytes(StandardCharsets.UTF_8));
                    count++;
                }
            } catch (IOException e) {
                log.error("写入文件失败 => {}", e.getMessage());
            } finally {
                lock.unlock();
            }
        } else {
            sleep(1000);
            writeToStream(out, content, ruleUrl);
        }
    }

    /**
     * 按路径创建文件，如存在则删除重新创建
     *
     * @param path 路径
     * @return {@link File}
     */
    public static File createFile(String path) {
        path = FileUtil.normalize(path);
        if (!FileUtil.isAbsolutePath(path)) {
            path = Constant.ROOT_PATH + File.separator + path;
        }
        File file = FileUtil.file(FileUtil.normalize(path));
        return createFile(file);
    }

    public static File createFile(File file) {
        if (FileUtil.exist(file)) {
            FileUtil.del(file);
        }
        FileUtil.touch(file);
        FileUtil.appendUtf8String(StrUtil.format(Constant.UPDATE,
                DateTime.now().toString(DatePattern.NORM_DATETIME_PATTERN)), file);
        FileUtil.appendUtf8String(Constant.REPO, file);
        return file;
    }

    /**
     * 校验内容是指定类型规则
     *
     * @param rule 内容
     * @param type 规则
     * @return 结果
     */
    public static boolean validRule(String rule, RuleType type) {

        //匹配标识，有标识时必须全部匹配
        if (!type.getIdentify().isEmpty() &&
                type.getIdentify().stream().noneMatch(rule::contains)) {
            return false;
        }

        //匹配正规则，需要至少满足一个
        if (!type.getMatch().isEmpty() &&
                type.getMatch().stream().noneMatch(pattern -> ReUtil.contains(pattern, rule))) {
            return false;
        }

        //匹配过滤规则，需要全部不满足
        return type.getExclude().isEmpty() ||
                type.getExclude().stream().noneMatch(pattern -> ReUtil.contains(pattern, rule));

    }

    /**
     * 清理rule字符串，去除空格和某些特定符号
     *
     * @param content 内容
     * @return 结果
     */
    public static String clearRule(String content) {
        content = StrUtil.isNotBlank(content) ? StrUtil.trim(content) : StrUtil.EMPTY;

        //有效性检测
        if (ReUtil.contains(Constant.EFFICIENT_REGEX, content)) {
            return StrUtil.EMPTY;
        }

        //去除首尾 基础修饰符号
        if (ReUtil.contains(Constant.BASIC_MODIFY_REGEX, content)) {
            content = ReUtil.replaceAll(content, Constant.BASIC_MODIFY_REGEX, StrUtil.EMPTY);
        }

        return StrUtil.trim(content);
    }

    public static <K, T> void safePut(Map<K, Set<T>> map, K key, T val) {
        if (map.containsKey(key)) {
            map.get(key).add(val);
        } else {
            map.put(key, CollUtil.newHashSet(val));
        }
    }

    /**
     * 计算 Bloom Filter的bit位数m
     *
     * <p>See <a href="http://en.wikipedia.org/wiki/Bloom_filter#Probability_of_false_positives">...</a> for the
     * formula.
     *
     * @param n 预期数据量
     * @param p 误判率 (must be 0 < p < 1)
     */
    static long optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }


    /**
     * 计算最佳k值，即在Bloom过滤器中插入的每个元素的哈希数
     *
     * <p>See <a href="http://en.wikipedia.org/wiki/File:Bloom_filter_fp_probability.svg">...</a> for the formula.
     *
     * @param n 预期数据量
     * @param m bloom filter中总的bit位数 (must be positive)
     */
    static int optimalNumOfHashFunctions(long n, long m) {
        // (m / n) * log(2), but avoid truncation due to division!
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    public static void safeClose(BufferedOutputStream bufferedOutputStream) {
        if (bufferedOutputStream != null) {
            try {
                bufferedOutputStream.close();
            } catch (IOException e) {
                log.error("关闭流出错，{}", e.getMessage());
            }
        }
    }
}
