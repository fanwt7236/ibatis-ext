package com.ibatis.ext.proxy;

import static org.springframework.util.Assert.notNull;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

/**
 * 基于spring的ibatis DAO扫描器<br>
 * 生成实现了dao接口实例并添加到spring容器<br>
 * ibatis DAO 及其 sqlMap文件须遵循以下规则:<br>
 * sqlMap文件的namespace == DAO接口的namespace<br>
 * sqlMap中sql的id == DAO接口中方法的名称<br>
 * DAO方法中如需传入多个参数，则使用@param注解标记参数<br>
 * DAO方法如需实现原queryForMap方法,则使用@Key注解标记方法,注解内填写作为key的字段名<br>
 * @author fanwt7236@163.com
 *
 */
public class Scanner implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware {

    private final Logger logger = Logger.getLogger(Scanner.class.getName());
    
    private ApplicationContext applicationContext;
    /** 接口所在的包名，多个包名使用','或';'隔开 */
    private String interPackage;
    /** sqlMapClient对应bean的id */
    private String sqlMapClient;

    public void setInterPackage(String interPackage) {
        this.interPackage = interPackage;
    }
    
    public void setSqlMapClient(String sqlMapClient) {
        this.sqlMapClient = sqlMapClient;
    }

    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }


    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    public void afterPropertiesSet() throws Exception {
        notNull(this.interPackage, "Property 'interPackage' is required");
        notNull(this.sqlMapClient, "Property 'sqlMapClient' is required");
    }
    
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        logger.info("Scanner has already started working![interfacePackage:"+this.interPackage+"]");
        InterfaceScanner scanner = new InterfaceScanner(registry);
        scanner.setResourceLoader(this.applicationContext);
        scanner.scan(StringUtils.tokenizeToStringArray(this.interPackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }

    final class InterfaceScanner extends ClassPathBeanDefinitionScanner {

        public InterfaceScanner(BeanDefinitionRegistry registry) {
            super(registry);
        }


        protected void registerDefaultFilters() {
            boolean acceptAllInterfaces = true;
            if (acceptAllInterfaces) {
                addIncludeFilter(new TypeFilter() {
                    public boolean match(MetadataReader metadataReader,
                            MetadataReaderFactory metadataReaderFactory) throws IOException {
                        return true;
                    }
                });
            }
            addExcludeFilter(new TypeFilter() {
                public boolean match(MetadataReader metadataReader,
                        MetadataReaderFactory metadataReaderFactory) throws IOException {
                    String className = metadataReader.getClassMetadata().getClassName();
                    return className.endsWith("package-info");
                }
            });
        }


        @Override
        protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        	Scanner.this.logger.info("interPackages scanning...");
        	Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
            for (BeanDefinitionHolder holder : beanDefinitions) {
                GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
                definition.getPropertyValues().add("mapperInterface", definition.getBeanClassName());
                definition.getPropertyValues().add("client", new RuntimeBeanReference(Scanner.this.sqlMapClient));
                definition.setBeanClass(MapperFactoryBean.class);
            }
            return beanDefinitions;
        }


        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return (beanDefinition.getMetadata().isInterface()
                    && beanDefinition.getMetadata().isIndependent());
        }


        @Override
        protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition)
                throws IllegalStateException {
            if (super.checkCandidate(beanName, beanDefinition)) {
                return true;
            } else {
                return false;
            }
        }
    }

}
