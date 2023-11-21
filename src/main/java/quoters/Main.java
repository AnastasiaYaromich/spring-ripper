package quoters;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("context.xml");
    }
}

/**
 * Когда поднимается ClassPathXmlApplicationContext приходит
 * XmlBeanDefinitionReader и считывает(парсирует) с xml все декларации бинов в xml
 * и кладет их в Map.
 *
 * XmlBeanDefinitionReader - внутренний компонент Spring'a,
 * который сканирует xml и создает bean definition's.
 * Bean Definition's - объекты, которые хранят информацию про бины
 * (как создавать). Аналог класса.
 *
 * BeanFactory из bean definitions сначала вытаскивает те
 * которые имплементируют интерфейс
 * beanpostprocessor, создает из них бины и кладет в сторонку.
 * И их помощью потом настраивает все остальные бины.
 * Каждый бин до вызова init-methoda проходит через всю цепочку
 * beanpostprocessors, после вызывается init-method, а потом
 * происходит еще один проход через цепочку beanpostprocessors.
 *
 * И итоге получаются полносью настроеные объекты.
 *
 *  После того как bean definition's созданы, beanFactory начинает
 *  по ним работать - созадет бины и складывает их в IoC контейнер,
 *  если это Singleton'ы. Propotype beans создаются в только с тот
 *  момент когда они нужны и не хранятся в контейнере.
 *  Если для Propotype beans прописан destroy-method то он работать
 *  не будет. В момент закрытия контекста Spring проходит по всем
 *  бинам которые хранятся в контейнере (а это только Singleton бины)
 *  находит их destroy-methods если они прописаны и запускает, а prototype
 *  в контейнере нет и поэтому для prototype destroy methods никогда не
 *  работает.
 *
 * BeanPostProcessor - позводяет настраивать beans до того как они
 * попадут в IoC container. BeanPostProcessor относится к аннотациям
 * класса и в момент создания bean'a настраивает его через аннотации.
 * Чтобы создать свой beanPostProcessor нужно имплементировать
 * интерфейс BeanPostProcessor. У данного интерфейса есть два метода
 * postProcessBefore и postProcessAfter Initialization.
 * postProcessBeforeInitialization() - вызывается до init-method'a.
 * postProcessAfterInitialization() - после init-method'a.
 *
 * Есть разные способы прописать init-method:
 * 1. Через атрибут init-method в теге bean, если работаем с xml.
 * 2. Через аннотацию postConstruct
 * 3. Имплементировать интерфейс initializingBean и прописать
 * метод afterPropertiesSet().
 *
 * Зачем нужны init-methods если есть конструктор?
 *  Что буцдет если в конструкторе обратиться к чем-то что настраивает
 *  Spring? 0 либо null pointer exception.
 *  Потому-что сначала сканируется xml, создаются bean definitions,
 *  при помощи reflection api запускаются конструкторы и создаются bean'ы
 *  на основании bean definitions. И уже созданные bean's настраиваются
 *  beanpostprocessor's.
 *  И если в контсрукторе обратиться к каким то вещам которые должен
 *  настроить Spring - получим либо nullpointerexception либо 0.
 *
 * Поэтому вместо того чтобы пользоваться конструктором мы может написать
 * init-method.
 * Но aннотация PostConstruct работать не будетю
 * Потому-что умолчанию xml не знает ни про какие аннотации,
 * про аннотации знают только bean post processors.
 * Аннотацию PostConstruct должен обрабатывать какой то
 * beanpostprocessor и для того чтобы его активизировать нужно в
 * xml прописать bean CommonAnnotationPostProcessor.
 * Кроме тех что мы сами пишем есть и другие beanpostprocesor's
 * (к аннотациям Transactional, Autowired и т.д) и если
 *  для каждой аннтоации прописывать в контекст beanpostprocessor
 *  захочется выпить пивка очень быстро.
 *  Поэтому прописывается просто namespace - annotation-config.
 *  Это namespace просто прячет кусок xml который добавляет в контекст
 *  все beanpostprocesors.
 *
 * Чтобы кастомный класс являлся частью системы которая созадет и
 * настраивает бины нужно просто прописать его в контексте.
 *
 * Зачем два прохода по beanpostprocessors?
 * Например нам нужно чтобы все методы классов над которыми
 * стоит аннотация profiling профилилоровались т.е. чтобы в лог
 * выводилось время сколько метод работает.
 * Понятно что будет beanPostProcessor который будет к этой аннотации относитьсяю
 * Он будет получать bean от beanFactory и спрашивать
 * не стоит ли над классом этого bean'a аннотация Profiling и если
 * стоит то ему придется в каждый метод данного bean'a дописывать
 * логику связанную с профилированием.
 *
 * Как добавить логику в существующий обьект?
 * Нужно будет на лету генерировать новый класс который либо будет
 * наследоваться от оригинального класса и переопределять его
 * методы и добавлять туда нужную логику либо он должен
 * имплементировать те же самые интерфейсы.
 * Подход с имплементированем интерфейсов называет Dynamic Proxy.
 * Второй с наслдеованием от оригинального класса - Cglib.
 * Если появляются beanpostprocessors которые могут взять и заменить
 * оригинальный класс то это делается в методе postProcessAfterInintialization().
 * Мы в данном случае знаем что PostConstruct всегда выполняется
 * на оригинальном объекте.
 *
 * Статический метод Proxy.newProxyInstance() - создает объект класса, который
 * сам же сгенерировал на лету. Метод принимает ClassLoader с помощью которого
 * сгенерированный на лету класс загрузится в хип, список интертерфейсов которые
 * должен имплементировать класс сгенерированный на лету, и InvocationHandler - объект,
 * который инкапсулирует логику которая попадет во всех методы класса который
 * сгенерится на лету.
 * Полученный Proxy класс будет помещен в IoC-контейнер.
 *
 * getBean() нужно запрашивать по интерфейсу потому что
 * вместо объекта оригинального класса в IoC-контейнере может
 * лежать Proxy.
 *
 * Таким образом beanPostProcessor's могут не только bean подкрутить
 * но и поменять логику его класса, если знать про DynamicProxy, cglib.
 *
 * ApplicationListener - компонент который умеет слушать контекст Spring'a.
 * С контекстом могут происходить разные события:
 * 1. ContextStartedEvent - контекст начал построение
 * 2. ContextStoppedEvent - контекст закончил построение
 * 3. ContextRefreshedEvent - контекст рефрешнулся.
 * Зачем? Допустим у нас есть метод в класе над которым стоит аннотация
 * Transactional.В какой момент beanPostProcessor который за аннотацию
 * Transactional отвечает логику связанную с транзакцией запихает в этот класс?
 * После того как PostConstruct отработает. Т.е. в методе
 * beanProcessAfterInitialization(). Получается PostConstruct отрабатывает
 * до того как настроилось Proxy.
 * Допустим мы хотим иметь третью фазу конструктора.
 * Допустим нам нужно чтобы все методы аннотированные аннотацией
 * PostProxy запускались сами в тот момент когда уже все
 * абсолютно настроено и все Proxy сгенерировались.
 * И это может делать только ContextListener.
 *
 *
 * Итак сначала работает обычный конструктор Java.
 * потом PostConstruct - за который отвечает BeanPostProcessor.
 * потом третий конструктор - @AfterProxy за который отвечает ContextListener.
 *
 * 4. ContextClosedEvent
 *
 *
 *
 *
 */