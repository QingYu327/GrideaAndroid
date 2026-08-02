package com.gridea.android.util

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * URL slug 工具
 *
 * 对应旧版 Gridea 0.9.3 的 src/helpers/slug.ts。
 *
 * 设计目标：把人类可读的标题（可能含中文、英文、数字、标点、空格）转成
 * URL-safe 的 slug，用作文章的 fileName（同时是 URL 路径段）。
 *
 * 处理流程：
 * 1. 标题中的中文常用字 → 拼音（带内置常用字拼音表）
 * 2. ASCII 字符原样保留
 * 3. 小写化、特殊符号替换为 `-`、合并连续 `-`、去除首尾 `-`
 *
 * 若结果为空（纯符号/无拼音映射的生僻字），返回 null 由调用方决定兜底策略。
 */
object SlugUtils {

    /**
     * 常用汉字 → 拼音（无声调）映射表。
     * 只取首个读音（多音字取最常用读音）。
     * 覆盖常用标题字场景，生僻字回退到短 ID 保证唯一性。
     */
    private val PINYIN_MAP: Map<Char, String> = buildPinyinMap()

    /** 自增计数器，用于同毫秒内生成不同短 ID */
    private val counter = AtomicInteger(0)

    /**
     * 将标题转为 URL slug。
     * @param title 原始标题（可能含中文/英文/数字/标点）
     * @return slug 字符串（如 "hello-shi-jie"）；若无法生成有效 slug，返回 null
     */
    fun slugify(title: String): String? {
        if (title.isBlank()) return null

        // 1. 中文转拼音（中文字符前后加空格分隔）
        val builder = StringBuilder(title.length * 2)
        for (c in title) {
            val pinyin = PINYIN_MAP[c]
            if (pinyin != null) {
                builder.append(' ').append(pinyin).append(' ')
            } else {
                builder.append(c)
            }
        }
        val pinyinStr = builder.toString()

        // 2. 小写化
        val lower = pinyinStr.lowercase()

        // 3. 把所有非 [a-z0-9] 字符替换为 `-`
        val replaced = lower.map { c ->
            if (c in 'a'..'z' || c in '0'..'9') c else '-'
        }.joinToString("")

        // 4. 合并连续 `-`、去除首尾 `-`
        val slug = replaced.replace(Regex("-+"), "-").trim('-')

        return if (slug.isNotEmpty()) slug else null
    }

    /**
     * 生成短 ID，作为 slugify 失败时的兜底。
     * 格式：8 位十六进制（MD5 前 4 字节）+ 4 位计数器，共 12 字符。
     */
    fun generateShortId(): String {
        val timestamp = System.currentTimeMillis().toString()
        val counterVal = counter.incrementAndGet() and 0xFFFF
        val md5 = MessageDigest.getInstance("MD5").digest(timestamp.toByteArray())
        val hex = md5.joinToString("") { "%02x".format(it) }.take(8)
        return "$hex${counterVal.toString(16).padStart(4, '0')}"
    }

    /**
     * 校验 slug 是否符合 URL-safe 规则。
     * 合法规则：`^[a-z0-9]+(-[a-z0-9]+)*$`
     */
    fun isValidSlug(slug: String): Boolean {
        return slug.isNotEmpty() && slug.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))
    }

    /**
     * 净化用户输入的 slug：
     * - 小写化、空格替换为 `-`、去除非法字符、合并连续 `-`、去首尾 `-`
     * 若净化后为空返回 null。
     */
    fun sanitize(input: String): String? {
        if (input.isBlank()) return null
        val lower = input.lowercase().trim()
        val replaced = lower.map { c ->
            when {
                c in 'a'..'z' || c in '0'..'9' -> c
                c == '-' || c == ' ' -> '-'
                else -> '-'
            }
        }.joinToString("")
        val result = replaced.replace(Regex("-+"), "-").trim('-')
        return if (result.isNotEmpty()) result else null
    }

    /**
     * 构建常用汉字拼音映射表。
     * 注：仅包含常用字，多音字取最常用读音。
     */
    private fun buildPinyinMap(): Map<Char, String> {
        val data = arrayOf(
            // A
            "阿a", "爱ai", "安an", "岸an", "按an", "案an", "昂ang", "凹ao", "奥ao", "懊ao",
            // B
            "八ba", "巴ba", "吧ba", "把ba", "爸ba", "白bai", "百bai", "摆bai", "败bai", "拜bai",
            "班ban", "般ban", "板ban", "半ban", "办ban", "帮bang", "邦bang", "榜bang", "包bao", "宝bao",
            "保bao", "报bao", "抱bao", "暴bao", "爆bao", "杯bei", "北bei", "贝bei", "备bei", "背bei",
            "本ben", "笨ben", "崩beng", "逼bi", "鼻bi", "比bi", "笔bi", "必bi", "币bi", "闭bi",
            "避bi", "边bian", "编bian", "变bian", "便bian", "标biao", "表biao", "别bie", "宾bin", "兵bing",
            "冰bing", "丙bing", "饼bing", "病bing", "波bo", "播bo", "伯bo", "驳bo", "博bo", "薄bo",
            "卜bu", "补bu", "不bu", "步bu", "部bu",
            // C
            "猜cai", "才cai", "财cai", "菜cai", "餐can", "参can", "残can", "仓cang", "藏cang", "操cao",
            "草cao", "册ce", "侧ce", "测ce", "层ceng", "叉cha", "插cha", "茶cha", "查cha", "察cha",
            "差cha", "柴chai", "产chan", "阐chan", "长chang", "场chang", "常chang", "厂chang", "唱chang", "抄chao",
            "超chao", "朝chao", "潮chao", "炒chao", "车che", "彻che", "陈chen", "晨chen", "成cheng", "承cheng",
            "城cheng", "程cheng", "惩cheng", "吃chi", "迟chi", "池chi", "持chi", "尺chi", "齿chi", "赤chi",
            "翅chi", "冲chong", "虫chong", "崇chong", "宠chong", "抽chou", "仇chou", "愁chou", "丑chou", "臭chou",
            "出chu", "初chu", "除chu", "厨chu", "处chu", "触chu", "川chuan", "穿chuan", "传chuan", "船chuan",
            "窗chuang", "床chuang", "创chuang", "吹chui", "垂chui", "春chun", "纯chun", "蠢chun", "词ci", "辞ci",
            "慈ci", "此ci", "刺ci", "聪cong", "从cong", "凑cou", "粗cu", "促cu", "醋cu", "村cun",
            "存cun", "寸cun", "错cuo",
            // D
            "达da", "答da", "打da", "大da", "呆dai", "代dai", "带dai", "袋dai", "戴dai", "担dan",
            "单dan", "胆dan", "旦dan", "但dan", "诞dan", "淡dan", "蛋dan", "当dang", "挡dang", "党dang",
            "刀dao", "导dao", "岛dao", "倒dao", "到dao", "道dao", "盗dao", "稻dao", "得de", "德de",
            "灯deng", "登deng", "等deng", "低di", "敌di", "底di", "地di", "弟di", "第di", "帝di",
            "点dian", "电dian", "店dian", "典dian", "奠dian", "顶ding", "定ding", "订ding", "丢diu", "东dong",
            "冬dong", "动dong", "冻dong", "洞dong", "都dou", "斗dou", "豆dou", "逗dou", "都du", "毒du",
            "读du", "独du", "度du", "渡du", "短duan", "段duan", "断duan", "对dui", "队dui", "吨dun",
            "蹲dun", "顿dun", "多duo",
            // E
            "鹅e", "额e", "恶e", "饿e", "恩en", "儿er", "耳er", "二er",
            // F
            "发fa", "乏fa", "伐fa", "罚fa", "法fa", "帆fan", "翻fan", "凡fan", "烦fan", "繁fan",
            "反fan", "返fan", "犯fan", "饭fan", "泛fan", "方fang", "坊fang", "防fang", "妨fang", "房fang",
            "访fang", "放fang", "飞fei", "肥fei", "废fei", "费fei", "分fen", "芬fen", "坟fen", "粉fen",
            "奋fen", "份fen", "风feng", "封feng", "峰feng", "锋feng", "疯feng", "逢feng", "缝feng", "奉feng",
            "佛fo", "否fou", "夫fu", "肤fu", "扶fu", "服fu", "浮fu", "符fu", "府fu", "父fu",
            "付fu", "负fu", "妇fu", "富fu", "腹fu", "覆fu",
            // G
            "改gai", "盖gai", "概gai", "干gan", "甘gan", "杆gan", "肝gan", "赶gan", "感gan", "刚gang",
            "钢gang", "岗gang", "港gang", "高gao", "告gao", "哥ge", "歌ge", "革ge", "格ge", "个ge",
            "各ge", "根gen", "跟gen", "更geng", "工gong", "公gong", "功gong", "攻gong", "供gong", "宫gong",
            "恭gong", "共gong", "勾gou", "沟gou", "狗gou", "够gou", "估gu", "孤gu", "姑gu", "古gu",
            "谷gu", "股gu", "骨gu", "鼓gu", "固gu", "故gu", "顾gu", "瓜gua", "寡gua", "挂gua",
            "关guan", "观guan", "官guan", "管guan", "贯guan", "惯guan", "光guang", "广guang", "归gui", "规gui",
            "贵gui", "滚gun", "国guo", "果guo", "过guo",
            // H
            "哈ha", "孩hai", "海hai", "害hai", "寒han", "韩han", "含han", "汉han", "汗han", "好hao",
            "号hao", "喝he", "合he", "何he", "和he", "河he", "贺he", "黑hei", "很hen", "狠hen",
            "恨hen", "恒heng", "横heng", "衡heng", "轰hong", "红hong", "洪hong", "宏hong", "后hou", "候hou",
            "厚hou", "呼hu", "忽hu", "湖hu", "虎hu", "互hu", "户hu", "护hu", "花hua", "华hua",
            "化hua", "划hua", "话hua", "怀huai", "坏huai", "欢huan", "还huan", "环huan", "换huan", "黄huang",
            "回hui", "会hui", "汇hui", "婚hun", "活huo", "火huo", "或huo", "获huo", "霍huo",
            // J
            "几ji", "机ji", "鸡ji", "积ji", "基ji", "绩ji", "击ji", "激ji", "及ji", "吉ji",
            "级ji", "极ji", "即ji", "集ji", "急ji", "计ji", "记ji", "纪ji", "技ji", "际ji",
            "季ji", "既ji", "继ji", "寄ji", "加jia", "家jia", "假jia", "价jia", "架jia", "嫁jia",
            "坚jian", "间jian", "监jian", "建jian", "剑jian", "健jian", "键jian", "江jiang", "将jiang", "讲jiang",
            "奖jiang", "降jiang", "交jiao", "教jiao", "角jiao", "脚jiao", "叫jiao", "接jie", "节jie", "结jie",
            "截jie", "姐jie", "借jie", "介jie", "界jie", "今jin", "金jin", "紧jin", "进jin", "近jin",
            "尽jin", "劲jing", "经jing", "精jing", "景jing", "警jing", "净jing", "竞jing", "敬jing", "静jing",
            "境jing", "镜jing", "九jiu", "久jiu", "酒jiu", "旧jiu", "救jiu", "就jiu", "居ju", "局ju",
            "举ju", "句ju", "具ju", "聚ju", "卷juan", "决jue", "绝jue", "军jun", "均jun",
            // K
            "开kai", "凯kai", "看kan", "康kang", "抗kang", "考kao", "靠kao", "科ke", "可ke", "克ke",
            "课ke", "客ke", "肯ken", "空kong", "孔kong", "恐kong", "口kou", "哭ku", "苦ku", "酷ku",
            "裤ku", "夸kua", "快kuai", "宽kuan", "款kuan", "矿kuang", "况kuang", "亏kui", "困kun", "扩kuo",
            "括kuo",
            // L
            "拉la", "啦la", "来lai", "蓝lan", "兰lan", "拦lan", "栏lan", "览lan", "懒lan", "烂lan",
            "浪lang", "劳lao", "老lao", "乐le", "雷lei", "类lei", "冷leng", "离li", "理li", "里li",
            "力li", "历li", "立li", "丽li", "利li", "例li", "连lian", "脸lian", "练lian", "良liang",
            "两liang", "亮liang", "谅liang", "量liang", "辽liao", "料liao", "列lie", "劣lie", "猎lie", "林lin",
            "临lin", "伶ling", "灵ling", "岭ling", "领ling", "令ling", "留liu", "流liu", "六liu", "楼lou",
            "漏lou", "露lu", "录lu", "路lu", "驴lu", "旅lu", "律lu", "绿lu", "乱luan", "略lue",
            "轮lun", "论lun", "罗luo", "落luo",
            // M
            "妈ma", "麻ma", "马ma", "骂ma", "嘛ma", "吗ma", "买mai", "卖mai", "满man", "慢man",
            "忙mang", "猫mao", "毛mao", "冒mao", "贸mao", "么me", "没mei", "每mei", "美mei", "妹mei",
            "门men", "们men", "梦meng", "迷mi", "米mi", "密mi", "秘mi", "棉mian", "免mian", "面mian",
            "苗miao", "秒miao", "庙miao", "妙miao", "灭mie", "民min", "敏min", "明ming", "名ming", "命ming",
            "摸mo", "模mo", "末mo", "莫mo", "墨mo", "默mo", "母mu", "目mu", "木mu",
            // N
            "拿na", "那na", "哪na", "奶nai", "南nan", "男nan", "难nan", "脑nao", "闹nao", "内nei",
            "嫩nen", "能neng", "你ni", "年nian", "念nian", "娘niang", "鸟niao", "尿niao", "捏nie", "您nin",
            "牛niu", "农nong", "女nu",
            // O
            "欧ou", "偶ou",
            // P
            "怕pa", "拍pai", "排pai", "牌pai", "派pai", "盘pan", "旁pang", "胖pang", "抛pao", "跑pao",
            "朋peng", "棚peng", "蓬peng", "批pi", "皮pi", "疲pi", "匹pi", "屁pi", "篇pian", "偏pian",
            "便pian", "片pian", "票piao", "拼pin", "品pin", "平ping", "评ping", "屏ping", "瓶ping", "破po",
            "普pu",
            // Q
            "七qi", "期qi", "齐qi", "奇qi", "骑qi", "旗qi", "乞qi", "企qi", "起qi", "气qi",
            "弃qi", "器qi", "卡qia", "千qian", "签qian", "前qian", "钱qian", "浅qian", "遣qian", "欠qian",
            "枪qiang", "强qiang", "墙qiang", "抢qiang", "悄qiao", "敲qiao", "桥qiao", "巧qiao", "切qie", "且qie",
            "窃qie", "亲qin", "勤qin", "寝qin", "青qing", "清qing", "轻qing", "倾qing", "情qing", "晴qing",
            "请qing", "庆qing", "秋qiu", "球qiu", "求qiu", "区qu", "曲qu", "取qu", "趣qu", "全quan",
            "权quan", "泉quan", "缺que", "却que", "确que", "群qun",
            // R
            "然ran", "燃ran", "染ran", "让rang", "绕rao", "热re", "人ren", "忍ren", "认ren", "任ren",
            "扔reng", "仍reng", "日ri", "荣rong", "容rong", "融rong", "肉rou", "如ru", "乳ru", "入ru",
            "软ruan", "锐rui", "润run", "若ruo", "弱ruo",
            // S
            "撒sa", "洒sa", "赛sai", "三san", "散san", "桑sang", "丧sang", "扫sao", "色se", "森sen",
            "杀sha", "沙sha", "傻sha", "晒shai", "山shan", "删shan", "闪shan", "陕shan", "善shan", "伤shang",
            "商shang", "上shang", "烧shao", "稍shao", "少shao", "绍shao", "设she", "射she", "涉she", "摄she",
            "申shen", "身shen", "深shen", "神shen", "沈shen", "审shen", "婶shen", "肾shen", "甚shen", "慎shen",
            "升sheng", "生sheng", "声sheng", "胜sheng", "盛sheng", "剩sheng", "圣sheng", "师shi", "失shi", "诗shi",
            "施shi", "狮shi", "湿shi", "十shi", "石shi", "时shi", "识shi", "实shi", "拾shi", "使shi",
            "始shi", "世shi", "市shi", "事shi", "势shi", "视shi", "试shi", "饰shi", "室shi", "释shi",
            "收shou", "手shou", "守shou", "首shou", "寿shou", "受shou", "授shou", "售shou", "瘦shou", "书shu",
            "叔shu", "殊shu", "抒shu", "舒shu", "疏shu", "输shu", "蔬shu", "熟shu", "暑shu", "署shu",
            "鼠shu", "属shu", "术shu", "述shu", "树shu", "束shu", "数shu", "刷shua", "耍shua", "衰shuai",
            "甩shuai", "帅shuai", "栓shuan", "双shuang", "霜shuang", "爽shuang", "谁shui", "水shui", "睡shui", "顺shun",
            "说shuo", "朔shuo", "硕shuo", "斯si", "撕si", "思si", "私si", "司si", "丝si", "四si",
            "寺si", "似si", "嗣si", "松song", "宋song", "送song", "诵song", "搜sou", "苏su", "俗su",
            "素su", "速su", "宿su", "塑su", "酸suan", "蒜suan", "算suan", "虽sui", "随sui", "岁sui",
            "碎sui", "孙sun", "损sun", "缩suo", "索suo", "锁suo", "所suo",
            // T
            "他ta", "她ta", "它ta", "塌ta", "塔ta", "踏ta", "胎tai", "台tai", "太tai", "泰tai",
            "谈tan", "弹tan", "痰tan", "坦tan", "叹tan", "汤tang", "堂tang", "塘tang", "糖tang", "烫tang",
            "掏tao", "涛tao", "逃tao", "桃tao", "陶tao", "淘tao", "讨tao", "套tao", "特te", "腾teng",
            "梯ti", "踢ti", "提ti", "题ti", "蹄ti", "体ti", "替ti", "剃ti", "天tian", "填tian",
            "田tian", "甜tian", "挑tiao", "条tiao", "跳tiao", "贴tie", "铁tie", "厅ting", "听ting", "停ting",
            "挺ting", "通tong", "同tong", "童tong", "铜tong", "统tong", "痛tong", "头tou", "投tou", "透tou",
            "秃tu", "突tu", "图tu", "徒tu", "涂tu", "土tu", "吐tu", "推tui", "腿tui", "退tui",
            "吞tun", "屯tun", "拖tuo", "脱tuo", "妥tuo", "拓tuo",
            // W
            "挖wa", "瓦wa", "外wai", "完wan", "玩wan", "晚wan", "网wang", "王wang", "往wang", "忘wang",
            "旺wang", "望wang", "危wei", "威wei", "为wei", "韦wei", "违wei", "围wei", "尾wei", "委wei",
            "卫wei", "未wei", "位wei", "味wei", "畏wei", "谓wei", "喂wei", "慰wei", "蔚wei", "文wen",
            "闻wen", "问wen", "翁weng", "我wo", "卧wo", "握wo", "污wu", "屋wu", "无wu", "吴wu",
            "武wu", "五wu", "午wu", "舞wu", "物wu", "悟wu", "误wu", "雾wu",
            // X
            "夕xi", "西xi", "吸xi", "希xi", "析xi", "息xi", "牺xi", "悉xi", "惜xi", "稀xi",
            "溪xi", "锡xi", "习xi", "喜xi", "戏xi", "系xi", "细xi", "狭xia", "下xia", "夏xia",
            "先xian", "鲜xian", "闲xian", "嫌xian", "现xian", "线xian", "献xian", "县xian", "相xiang", "香xiang",
            "乡xiang", "详xiang", "想xiang", "向xiang", "项xiang", "象xiang", "像xiang", "小xiao", "晓xiao", "孝xiao",
            "效xiao", "笑xiao", "些xie", "歇xie", "协xie", "邪xie", "胁xie", "写xie", "血xie", "卸xie",
            "屑xie", "谢xie", "心xin", "辛xin", "欣xin", "新xin", "信xin", "兴xing", "星xing", "行xing",
            "形xing", "型xing", "醒xing", "杏xing", "性xing", "姓xing", "胸xiong", "雄xiong", "修xiu", "秀xiu",
            "绣xiu", "锈xiu", "臭xiu", "许xu", "须xu", "需xu", "续xu", "轩xuan", "宣xuan", "悬xuan",
            "选xuan", "炫xuan", "学xue", "雪xue", "血xue", "寻xun", "巡xun", "询xun", "循xun", "训xun",
            "迅xun", "讯xun",
            // Y
            "压ya", "呀ya", "押ya", "牙ya", "亚ya", "烟yan", "淹yan", "延yan", "言yan", "严yan",
            "炎yan", "沿yan", "眼yan", "演yan", "艳yan", "燕yan", "央yang", "扬yang", "羊yang", "阳yang",
            "杨yang", "洋yang", "仰yang", "养yang", "样yang", "腰yao", "邀yao", "摇yao", "咬yao", "药yao",
            "要yao", "耶ye", "也ye", "页ye", "业ye", "叶ye", "夜ye", "一yi", "伊yi", "衣yi",
            "医yi", "依yi", "仪yi", "宜yi", "姨yi", "移yi", "遗yi", "疑yi", "已yi", "乙yi",
            "以yi", "艺yi", "忆yi", "议yi", "益yi", "异yi", "役yi", "译yi", "易yi", "疫yi",
            "意yi", "毅yi", "因yin", "阴yin", "音yin", "银yin", "引yin", "饮yin", "印yin", "应ying",
            "英ying", "樱ying", "鹰ying", "迎ying", "赢ying", "影ying", "映ying", "硬ying", "哟yo", "用yong",
            "优you", "忧you", "悠you", "由you", "邮you", "油you", "游you", "友you", "有you", "又you",
            "右you", "幼you", "于yu", "予yu", "余yu", "鱼yu", "愉yu", "愚yu", "榆yu", "与yu",
            "宇yu", "语yu", "玉yu", "育yu", "欲yu", "遇yu", "御yu", "裕yu", "誉yu", "元yuan",
            "原yuan", "源yuan", "圆yuan", "员yuan", "园yuan", "缘yuan", "远yuan", "怨yuan", "院yuan", "愿yuan",
            "月yue", "乐yue", "岳yue", "跃yue", "越yue", "云yun", "匀yun", "允yun", "运yun", "韵yun",
            // Z
            "杂za", "砸za", "咱zan", "赞zan", "脏zang", "葬zang", "遭zao", "早zao", "藻zao", "造zao",
            "噪zao", "燥zao", "责ze", "择ze", "泽ze", "贼zei", "怎zen", "增zeng", "憎zeng", "赠zeng",
            "扎zha", "闸zha", "眨zha", "炸zha", "榨zha", "斋zhai", "摘zhai", "宅zhai", "窄zhai", "债zhai",
            "寨zhai", "沾zhan", "粘zhan", "盏zhan", "斩zhan", "展zhan", "占zhan", "战zhan", "站zhan", "张zhang",
            "章zhang", "长zhang", "涨zhang", "掌zhang", "丈zhang", "仗zhang", "帐zhang", "障zhang", "招zhao", "找zhao",
            "召zhao", "赵zhao", "照zhao", "罩zhao", "遮zhe", "折zhe", "哲zhe", "者zhe", "这zhe", "浙zhe",
            "贞zhen", "针zhen", "侦zhen", "珍zhen", "真zhen", "砧zhen", "诊zhen", "枕zhen", "阵zhen", "振zhen",
            "镇zhen", "震zhen", "争zheng", "征zheng", "挣zheng", "睁zheng", "筝zheng", "蒸zheng", "整zheng", "正zheng",
            "证zheng", "郑zheng", "政zheng", "之zhi", "支zhi", "汁zhi", "芝zhi", "枝zhi", "知zhi", "织zhi",
            "脂zhi", "蜘zhi", "执zhi", "直zhi", "值zhi", "职zhi", "植zhi", "殖zhi", "止zhi", "只zhi",
            "旨zhi", "址zhi", "纸zhi", "指zhi", "至zhi", "志zhi", "帜zhi", "制zhi", "质zhi", "治zhi",
            "中zhong", "忠zhong", "终zhong", "钟zhong", "衷zhong", "肿zhong", "种zhong", "重zhong", "众zhong", "周zhou",
            "州zhou", "洲zhou", "粥zhou", "轴zhou", "肘zhou", "咒zhou", "宙zhou", "昼zhou", "皱zhou", "骤zhou",
            "珠zhu", "株zhu", "蛛zhu", "猪zhu", "竹zhu", "筑zhu", "逐zhu", "主zhu", "煮zhu", "嘱zhu",
            "住zhu", "助zhu", "注zhu", "驻zhu", "祝zhu", "著zhu", "蛀zhu", "铸zhu", "抓zhua", "拽zhuai",
            "专zhuan", "砖zhuan", "转zhuan", "赚zhuan", "撰zhuan", "庄zhuang", "装zhuang", "壮zhuang", "状zhuang", "撞zhuang",
            "追zhui", "锥zhui", "坠zhui", "赘zhui", "准zhun", "捉zhuo", "桌zhuo", "卓zhuo", "酌zhuo", "啄zhuo",
            "着zhuo", "资zi", "姿zi", "滋zi", "仔zi", "子zi", "紫zi", "字zi", "自zi", "宗zong",
            "综zong", "总zong", "纵zong", "走zou", "奏zou", "租zu", "足zu", "卒zu", "族zu", "阻zu",
            "组zu", "祖zu", "钻zuan", "嘴zui", "最zui", "罪zui", "醉zui", "尊zun", "遵zun", "昨zuo",
            "左zuo", "做zuo", "作zuo", "坐zuo", "座zuo"
        )
        val map = HashMap<Char, String>(data.size)
        for (entry in data) {
            if (entry.length < 2) continue
            val ch = entry[0]
            val pinyin = entry.substring(1)
            // 只保留首次出现的映射，避免覆盖
            if (!map.containsKey(ch)) {
                map[ch] = pinyin
            }
        }
        return map
    }
}
