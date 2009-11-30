/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping;

/**
 * {@link BeanDefinitionParser} that parses the {@code annotation-driven} element to configure a Spring MVC web
 * application.
 *
 * <p>Responsible for:
 * <ol>
 * <li>Registering a DefaultAnnotationHandlerMapping bean for mapping HTTP Servlet Requests to @Controller methods
 * using @RequestMapping annotations.
 * <li>Registering a AnnotationMethodHandlerAdapter bean for invoking annotated @Controller methods.
 * Will configure the HandlerAdapter's <code>webBindingInitializer</code> property for centrally configuring
 * {@code @Controller} {@code DataBinder} instances:
 * <ul>
 * <li>Configures the conversionService if specified, otherwise defaults to a fresh {@link ConversionService} instance
 * created by the default {@link FormattingConversionServiceFactoryBean}.
 * <li>Configures the validator if specified, otherwise defaults to a fresh {@link Validator} instance created by the
 * default {@link LocalValidatorFactoryBean} <em>if the JSR-303 API is present on the classpath</em>.
 * <li>Configures standard {@link org.springframework.http.converter.HttpMessageConverter HttpMessageConverters},
 * including the {@link Jaxb2RootElementHttpMessageConverter} <em>if JAXB2 is present on the classpath</em>, and
 * the {@link MappingJacksonHttpMessageConverter} <em>if Jackson is present on the classpath</em>.
 * </ul>
 * </ol>
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 3.0
 */
public class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	private static final boolean jsr303Present = ClassUtils.isPresent(
			"javax.validation.Validator", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jacksonPresent =
			ClassUtils.isPresent("org.codehaus.jackson.map.ObjectMapper", AnnotationDrivenBeanDefinitionParser.class.getClassLoader()) &&
					ClassUtils.isPresent("org.codehaus.jackson.JsonGenerator", AnnotationDrivenBeanDefinitionParser.class.getClassLoader());



	public BeanDefinition parse(Element element, ParserContext parserContext) {
		Object source = parserContext.extractSource(element);

		RootBeanDefinition annMappingDef = new RootBeanDefinition(DefaultAnnotationHandlerMapping.class);
		annMappingDef.setSource(source);
		annMappingDef.getPropertyValues().add("order", 0);
		String annMappingName = parserContext.getReaderContext().registerWithGeneratedName(annMappingDef);

		RootBeanDefinition bindingDef = new RootBeanDefinition(ConfigurableWebBindingInitializer.class);
		bindingDef.setSource(source);
		bindingDef.getPropertyValues().add("conversionService", getConversionService(element, source, parserContext));
		bindingDef.getPropertyValues().add("validator", getValidator(element, source, parserContext));

		RootBeanDefinition annAdapterDef = new RootBeanDefinition(AnnotationMethodHandlerAdapter.class);
		annAdapterDef.setSource(source);
		annAdapterDef.getPropertyValues().add("webBindingInitializer", bindingDef);
		annAdapterDef.getPropertyValues().add("messageConverters", getMessageConverters(source));
		String adapterName = parserContext.getReaderContext().registerWithGeneratedName(annAdapterDef);

		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
		parserContext.pushContainingComponent(compDefinition);
		parserContext.registerComponent(new BeanComponentDefinition(annMappingDef, annMappingName));
		parserContext.registerComponent(new BeanComponentDefinition(annAdapterDef, adapterName));
		parserContext.popAndRegisterContainingComponent();
		
		return null;
	}
	

	private Object getConversionService(Element element, Object source, ParserContext parserContext) {
		if (element.hasAttribute("conversion-service")) {
			return new RuntimeBeanReference(element.getAttribute("conversion-service"));
		}
		else {
			RootBeanDefinition conversionDef = new RootBeanDefinition(FormattingConversionServiceFactoryBean.class);
			conversionDef.setSource(source);
			String conversionName = parserContext.getReaderContext().registerWithGeneratedName(conversionDef);
			return new RuntimeBeanReference(conversionName);
		}
	}

	private Object getValidator(Element element, Object source, ParserContext parserContext) {
		if (element.hasAttribute("validator")) {
			return new RuntimeBeanReference(element.getAttribute("validator"));
		}
		else if (jsr303Present) {
			RootBeanDefinition validatorDef = new RootBeanDefinition(LocalValidatorFactoryBean.class);
			validatorDef.setSource(source);
			String validatorName = parserContext.getReaderContext().registerWithGeneratedName(validatorDef);
			return new RuntimeBeanReference(validatorName);
		}
		else {
			return null;
		}
	}

	private ManagedList<RootBeanDefinition> getMessageConverters(Object source) {
		ManagedList<RootBeanDefinition> messageConverters = new ManagedList<RootBeanDefinition>();
		messageConverters.setSource(source);
		messageConverters.add(new RootBeanDefinition(ByteArrayHttpMessageConverter.class));
		messageConverters.add(new RootBeanDefinition(StringHttpMessageConverter.class));
		messageConverters.add(new RootBeanDefinition(FormHttpMessageConverter.class));
		messageConverters.add(new RootBeanDefinition(SourceHttpMessageConverter.class));
		if (jaxb2Present) {
			messageConverters.add(new RootBeanDefinition(Jaxb2RootElementHttpMessageConverter.class));
		}
		if (jacksonPresent) {
			messageConverters.add(new RootBeanDefinition(MappingJacksonHttpMessageConverter.class));
		}
		return messageConverters;
	}

}
