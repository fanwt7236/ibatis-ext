## iBatis2.3.5原生版本扩展
### 扩展了对spring默认事务管理器的支持，com.ibatis.ext.transaction.DataSourcesTransactionManager 可以支持多数据源的n阶段提交。

	<bean id="txManager" class="com.ibatis.ext.transaction.DataSourcesTransactionManager">
		<property name="dataSources">
			<list>
				<ref bean="firstDataSource"/>
				<ref bean="secondDataSource"/>
			</list>
		</property>
	</bean>
### 扩展了ibatis二级缓存（memcached、redis）
### 实现了dao动态代理（参照MyBatis实现）
参照MyBatis实现了dao的jdk动态代理，在使用时需依赖spring相关的jar包。配置如下:  

    <bean class="com.ibatis.ext.proxy.Scanner">
		<property name="interPackage" value="com.xxx.dao"></property>
		<property name="sqlMapClient" value="sqlMapClient"></property>
	</bean>
	<bean id="sqlMapClient" class="org.springframework.orm.ibatis.SqlMapClientFactoryBean">
		<property name="dataSource" ref="dataSource" />
		<property name="configLocation" value="classpath:sqlMapConfig.xml" />
		<property name="mappingLocations">
			<list>
				<value>classpath:mapper/**/*.xml</value>
			</list>
		</property>
	</bean>
### 修改insert返回值
正如在改ibator时所说，ibatis中存在一个可优化的点:insert。原生iBatis在insert动作后会返回selectKey的结果（如果配了的话），但是我们通常可以在selectKey上添加属性javaProperty来给对象的属性进行复制，这样一来，再把结果返回显得有点多余（MyBatis中已经修改了此设计）。为了实现在insert时能够返回该条sql影响的记录数，我们需要对iBatis原有的实现进行重写，好在iBatis还有一些扩展的空间，可以在sqlMapConfig.xml文件中指定properties文件，并在properties文件中指定一个sql_executor_class来按照自己的想法执行程序。
现在我们可以来研究一下sql_executor_class配置相关的一些知识:
1.在哪里加载。通过阅读源码，不难发现sql_executor_class是在com.ibatis.sqlmap.engine.builder.xml.XmlParserState.setGlobalProperties()方法中进行读取的，读取之后执行了两个操作:
config.getClient().getDelegate().setCustomExecutor(customizedSQLExecutor);//设置自定义的sqlExecutor
config.getClient().getDelegate().getSqlExecutor().init(config, globalProps);//对自定义的sqlExecutor进行初始化。
请注意，init方法中传入的config参数就是SqlMapConfiguration对象，通过SqlMapConfiguration对象，我们可以拿到与SqlMapClient相关的一切信息（可能会用到反射）  
2.原生的insert方法的最终实现在哪里，又是在哪个文件中设置了insert方法的返回值。
通过跟踪源码，不难发现com.ibatis.sqlmap.engine.impl.SqlMapExecutorDelegate.insert中将selectKey的值返回了，因此我们只需要重写insert方法，并将其中ms.executeUpdate(statementScope, trans, param)的值返回即可。  
基于以上两点，我们可以重写insert方法的原有实现，将影响的记录数返回。在这里需要有一点需要特别注意的，请看下面的代码:

		//这里把原有client的执行代理进行了扩展，扩展的执行代理支持了insert返回影响记录数以及支持了真分页查询
		SqlMapExecutorDelegate delegate = new SqlMapExecutorDelegateExt(config.getDelegate(), this);
		config.getClient().delegate = delegate;
		try {
			Field field = SqlMapConfiguration.class.getDeclaredField("delegate");
			field.setAccessible(true);
			field.set(config, delegate);
			field = SqlMapConfiguration.class.getDeclaredField("typeHandlerFactory");
			field.setAccessible(true);
			field.set(config, delegate.getTypeHandlerFactory());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
上面的代码是init方法的部分实现，之所以要这样做是因为SqlMapExecutorDelegate在SqlMapClientImpl和SqlMapConfiguration中都有定义（并且是相同的对象），而在执行init方法的前后都会对delegate中的属性进行赋值。所以不但要修改了SqlMapClientImpl中的delegate，也要把SqlMapConfiguration的delegate以及与delegate关联的typeHandlerFactory进行修改。SqlMapClientImpl中delegate是public的，但SqlMapConfiguration中没有相应的set方法，因此通过反射技术对SqlMapConfiguration对象的属性进行修改。
### 支持真分页
在开发过程中，分页查询是应用非常普遍同时也非常模式化的一件事。而iBatis的默认分页是假分页，既然我们都已经对sqlExecutor有了初步的认识，我们就可以通过重写executeQuery方法来实现真分页。
我将在分页查询中用到的一些参数都封装在了一个叫做Page的类中，如pageNum,pageSize等，详情参见com.ibatis.ext.paging.Page，在executeQuery时根据Page对象进行组装sql从而实现真分页
那么如何在executeQuery方法中得到从位置地方传来的Page对象呢？这里用到的是ThreadLocal进行线程內的隐式传参，同时也配置了一个正则表达式用于和sqlMap文件中的statementId进行规则匹配，符合规则的才会进行分页查询（这样做是因为有场景需要查询全部数据）。详细参见代码吧!今天只进行了初步的测试，其中可能有不少bug，遇到时再修复吧！  
主要代码参见：com.ibatis.ext.SqlExecutorExt

