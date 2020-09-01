package simpleorm.utils;

import java.lang.reflect.Method;
import java.util.Collection;

import simpleorm.dataset.SFieldMeta;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SFieldScalar;

/** EXPERIMENTAL.  Should we have a bean reference in SRecordReference to allow pointers?
 * Convenience methods for extracting properties as a bean, if that is really necessary.  */
public class SBeanUtil {
//	static final SBeanUtil me = new SBeanUtil();
//	
//	   /**
//     *  Updates fields.values with bean properties in bean.
//     *  fields parameters is normally <code>getCrudFields().getValues()</code>
//     *  Propeties are methods of form getXxx() where Xxx is the field name.
//     *  Note silently ignores fields that do not have properties.
//     */
//    public void retrieveBeanProperties(Object bean, SRecordInstance inst) {
//        for (SFieldMeta field:  inst.getMeta().getFieldMetas()) {
//            try {
//                retrieveBeanProperty(inst, field, bean);
//            } catch (NoSuchMethodException nsm) { }
//            catch (Exception ex) {
//                throw new SException.Error("While retrieving " + field + " from " + bean + " into " + inst, ex);
//            }
//        }
//    }
//
//    /**
//     * Copies fields.values into bean properties.  Ignores methods that do not exist.
//     * @see #retrieveBeanProperties
//     */
//     public void updateBeanProperties(Object bean, SRecordInstance inst) {
//         for (SFieldMeta field: inst.getMeta().getFieldMetas()) {
//             try {
//            		 updateBeanProperty(bean, inst, field, Object.class); // todo get params should not need to be Object.
//             } catch (NoSuchMethodException nsm) { }
//             catch (Exception ex) {throw new SException.Error("While updating " + bean + "." + field + " from " + inst, ex);}
//         }
//    }
//
//    /** set this field's value from the Java bean (ie. setValue(bean.getName()).
//     * Support for getBeanProperties.
//     * Note that there is no real point in calling this directly for an individual property,
//     * better just to explicitly call its getter.
//     */
//    void retrieveBeanProperty(SRecordInstance inst, SFieldMeta field, Object bean) throws Exception {
//        inst.setObject(field, getPropertyValue(bean, field.getFieldName()));
//    }
//
//    /** set the Java bean's property to this field's value (ie. bean.setName(getValue())). */
//    void updateBeanProperty(Object bean, SRecordInstance inst, SFieldMeta field, Class param) throws Exception {
//        setPropertyValue(bean, field.getFieldName(), inst.getObject(field), param);
//    }
//
//
//	 /** get(foo, "bar") returns foo.getBar() */
//    Object getPropertyValue(Object bean, String name) throws Exception {
//            Method  method = propertyMethod(bean, "get", name, null);
//            return method.invoke(bean);
//    }
//
//    /** Does bean.setName(value).
//     * (clazz parameter is sadly necessary in case value is null.)
//     */
//    void setPropertyValue(Object bean, String name, Object value, Class param) throws Exception {
//            Method  method = propertyMethod(bean, "set", name, new Class[]{param});
//            method.invoke(bean, value);
//    }
//
//    Method propertyMethod(Object bean, String getSet, String name, Class[] params) throws Exception {
//        String mname = getSet + name.substring(0, 1).toUpperCase() + name.substring(1);
//        Class c = bean.getClass();
//        return c.getMethod(mname, params);
//    }

}
