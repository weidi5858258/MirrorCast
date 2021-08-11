package com.weidi.mirrorcast;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/***
 private Object onEvent(int what, Object[] objArray) {
 Object result = null;
 switch (what){
 default:
 }
 return result;
 }
 */

public class EventBusUtils {

    public static abstract class AAsyncResult implements Parcelable {

        public abstract void onResult(Object object);

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            return;
        }
    }

    public static void init() {
        EventBus.getDefault();
    }

    public static void register(Object object) {
        EventBus.getDefault().register(object);
    }

    public static void unregister(Object object) {
        EventBus.getDefault().unregister(object);
    }

    /***
     * 不需要去调用
     */
    public static void onDestroy() {
        EventBus.getDefault().onDestroy();
    }

    /***
     能得到结果(代码可能在UI线程或者Thread线程)
     @param what 消息标志
     @param objArray 传递的数据 传给onEvent(int what, Object[] objArray)的参数.
     */
    public static Object post(final Class clazz,
                              final int what,
                              final Object[] objArray) {
        return EventBus.getDefault().post(clazz, what, objArray);
    }

    /***
     能得到结果(代码可能在UI线程或者Thread线程)
     */
    public static Object post(final Object object,
                              final int what,
                              final Object[] objArray) {
        return EventBus.getDefault().post(object, what, objArray);
    }

    /***
     调用此方法是在UI线程时能得到结果,否则为null
     */
    public static Object postUi(final Class clazz,
                                final int what,
                                final Object[] objArray) {
        return EventBus.getDefault().postUi(clazz, what, objArray);
    }

    /***
     调用此方法是在UI线程时能得到结果,否则为null
     */
    public static Object postUi(final Object object,
                                final int what,
                                final Object[] objArray) {
        return EventBus.getDefault().postUi(object, what, objArray);
    }

    /***
     返回结果为null
     */
    public static Object postUiDelayed(final Class clazz,
                                       final int what,
                                       long delayMillis,
                                       final Object[] objArray) {
        return EventBus.getDefault().postUiDelayed(clazz, what, delayMillis, objArray);
    }

    /***
     返回结果为null
     */
    public static Object postUiDelayed(final Object object,
                                       final int what,
                                       long delayMillis,
                                       final Object[] objArray) {
        return EventBus.getDefault().postUiDelayed(object, what, delayMillis, objArray);
    }

    /***
     调用此方法是在Thread线程时能得到结果,否则为null
     */
    public static Object postThread(final Class clazz,
                                    final int what,
                                    final Object[] objArray) {
        return EventBus.getDefault().postThread(clazz, what, objArray);
    }

    /***
     调用此方法是在Thread线程时能得到结果,否则为null
     */
    public static Object postThread(final Object object,
                                    final int what,
                                    final Object[] objArray) {
        return EventBus.getDefault().postThread(object, what, objArray);
    }

    /***
     返回结果为null
     */
    public static Object postThreadDelayed(final Class clazz,
                                           final int what,
                                           long delayMillis,
                                           final Object[] objArray) {
        return EventBus.getDefault().postThreadDelayed(clazz, what, delayMillis, objArray);
    }

    /***
     返回结果为null
     */
    public static Object postThreadDelayed(final Object object,
                                           final int what,
                                           long delayMillis,
                                           final Object[] objArray) {
        return EventBus.getDefault().postThreadDelayed(object, what, delayMillis, objArray);
    }

}

//////////////////////////////////////////////////////////////////////////////////

class EventBus {

    private static final String TAG =
            EventBus.class.getSimpleName();

    private volatile static EventBus sEventBus;

    private HandlerThread mHandlerThread;
    private Handler mThreadHandler;
    private Handler mUiHandler;

    EventBus() {
        clear();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mThreadHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                EventBus.this.threadHandleMessage(msg);
            }
        };

        mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                EventBus.this.uiHandleMessage(msg);
            }
        };
    }

    static EventBus getDefault() {
        if (sEventBus == null) {
            synchronized (EventBus.class) {
                if (sEventBus == null) {
                    sEventBus = new EventBus();
                }
            }
        }
        return sEventBus;
    }

    //////////////////////////////////////////////////////////////////////////////////////

    /***
     下面的实现方式是定向型的，效率上面可能会高一些
     如果某种动作只在特定类里面发生，那么用下面的方式
     如果有多个类需要做相同的操作，那么用上面的方式
     1.不需要实现接口
     2.不同类之间的what相同也不会造成问题，如果用上面的方式就会有问题
     3.传递的数据都在主线程中执行
     */

    private static final HashMap<Message, Object[]> mThreadMsgMap =
            new HashMap<Message, Object[]>();
    private static final HashMap<Message, Object[]> mUiMsgMap =
            new HashMap<Message, Object[]>();
    private static final HashMap<Object, Method> mObjectMethodMap =
            new HashMap<Object, Method>();
    private volatile static Message sUiMessage = null;
    private volatile static Message sThreadMessage = null;
    private Object mObjResult;

    synchronized void register(Object object) {
        if (object == null) {
            throw new NullPointerException("EventBus register() object = null");
        }
        Class clazz = object.getClass();
        Method method = null;

        try {
            method = clazz.getDeclaredMethod("onEvent", int.class, Object[].class);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            method = null;
            Log.e(TAG, "EventBus register(): " + object + " NoSuchMethodException");
            e.printStackTrace();
        } catch (Exception e) {
            method = null;
            e.printStackTrace();
        }

        if (method == null || mObjectMethodMap == null) {
            return;
        }
        mObjectMethodMap.put(object, method);
    }

    synchronized void unregister(Object object) {
        if (object == null) {
            throw new NullPointerException("EventBus unregister() class = null");
        }
        if (mObjectMethodMap == null || mObjectMethodMap.isEmpty()) {
            return;
        }
        if (!mObjectMethodMap.containsKey(object)) {
            return;
        }

        String sampleName = object.getClass().getSimpleName();
        Iterator<Map.Entry<Object, Method>> iter = mObjectMethodMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            Object keyObject = entry.getKey();
            if (keyObject.getClass().getSimpleName().equals(sampleName)) {
                iter.remove();
                break;
            }
        }
    }

    /***
     * 不需要去调用
     */
    void onDestroy() {
        Log.i(TAG, "onDestroy()");
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    /***
     * 同步
     * 代码可能执行于UI线程或者Thread线程
     *
     * @param what 消息标志
     * @param objArray 传递的数据 传给onEvent(int what, Object[] objArray)的参数.
     */
    Object post(final Class clazz,
                final int what,
                final Object[] objArray) {
        return dispatchEvent(clazz, what, objArray);
    }

    Object post(final Object object,
                final int what,
                final Object[] objArray) {
        return dispatchEvent(object, what, objArray);
    }

    Object postUi(final Class clazz,
                  final int what,
                  final Object[] objArray) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return dispatchEvent(clazz, what, objArray);
        } else {
            sendMsgAtTimeByUi(
                    getUiMsg(clazz, what), SystemClock.uptimeMillis(), objArray);
            return null;
        }
    }

    Object postUi(final Object object,
                  final int what,
                  final Object[] objArray) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            return dispatchEvent(object, what, objArray);
        } else {
            sendMsgAtTimeByUi(
                    getUiMsg(object, what), SystemClock.uptimeMillis(), objArray);
            return null;
        }
    }

    Object postUiDelayed(final Class clazz,
                         final int what,
                         long delayMillis,
                         final Object[] objArray) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        sendMsgAtTimeByUi(
                getUiMsg(clazz, what), SystemClock.uptimeMillis() + delayMillis, objArray);
        return null;
    }

    Object postUiDelayed(final Object object,
                         final int what,
                         long delayMillis,
                         final Object[] objArray) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        sendMsgAtTimeByUi(
                getUiMsg(object, what), SystemClock.uptimeMillis() + delayMillis, objArray);
        return null;
    }

    Object postThread(final Class clazz,
                      final int what,
                      final Object[] objArray) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            sendMsgAtTimeByThread(
                    getThreadMsg(clazz, what), SystemClock.uptimeMillis(), objArray);
            return null;
        } else {
            return dispatchEvent(clazz, what, objArray);
        }
    }

    Object postThread(final Object object,
                      final int what,
                      final Object[] objArray) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            sendMsgAtTimeByThread(
                    getThreadMsg(object, what), SystemClock.uptimeMillis(), objArray);
            return null;
        } else {
            return dispatchEvent(object, what, objArray);
        }
    }

    Object postThreadDelayed(final Class clazz,
                             final int what,
                             long delayMillis,
                             final Object[] objArray) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        sendMsgAtTimeByThread(
                getThreadMsg(clazz, what), SystemClock.uptimeMillis() + delayMillis, objArray);
        return null;
    }

    Object postThreadDelayed(final Object object,
                             final int what,
                             long delayMillis,
                             final Object[] objArray) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        sendMsgAtTimeByThread(
                getThreadMsg(object, what), SystemClock.uptimeMillis() + delayMillis, objArray);
        return null;
    }

    private final Message getThreadMsg() {
        Message msg = null;
        if (sThreadMessage == null) {
            msg = mThreadHandler.obtainMessage();
            sThreadMessage = msg;
        } else {
            msg = Message.obtain(sThreadMessage);
        }
        return msg;
    }

    private final Message getThreadMsg(Class clazz, int what) {
        Message msg = getThreadMsg();
        msg.obj = clazz;
        msg.what = what;
        return msg;
    }

    private final Message getThreadMsg(Object object, int what) {
        Message msg = getThreadMsg();
        msg.obj = object;
        msg.what = what;
        return msg;
    }

    private final Message getUiMsg() {
        Message msg = null;
        if (sUiMessage == null) {
            msg = mUiHandler.obtainMessage();
            sUiMessage = msg;
        } else {
            msg = Message.obtain(sUiMessage);
        }
        return msg;
    }

    private final Message getUiMsg(Class clazz, int what) {
        Message msg = getUiMsg();
        msg.obj = clazz;
        msg.what = what;
        return msg;
    }

    private final Message getUiMsg(Object object, int what) {
        Message msg = getUiMsg();
        msg.obj = object;
        msg.what = what;
        return msg;
    }

    private final boolean sendMsgAtTimeByThread(final Message msg,
                                                final long uptimeMillis,
                                                final Object[] objArray) {
        synchronized (mThreadMsgMap) {
            mThreadMsgMap.put(msg, objArray);
        }
        return mThreadHandler.sendMessageAtTime(msg, uptimeMillis);
    }

    private final boolean sendMsgAtTimeByUi(final Message msg,
                                            final long uptimeMillis,
                                            final Object[] objArray) {
        synchronized (mUiMsgMap) {
            mUiMsgMap.put(msg, objArray);
        }
        return mUiHandler.sendMessageAtTime(msg, uptimeMillis);
    }

    void clear() {
        if (mObjectMethodMap != null) {
            synchronized (mObjectMethodMap) {
                mObjectMethodMap.clear();
            }
        }
    }

    private Object dispatchEvent(Class clazz, int what, Object[] objArray) {
        if (clazz == null) {
            return null;
        }

        String sampleName = clazz.getSimpleName();
        Iterator<Map.Entry<Object, Method>> iterator = mObjectMethodMap.entrySet().iterator();
        if (iterator == null) {
            return null;
        }
        while (iterator.hasNext()) {
            Map.Entry<Object, Method> entry = iterator.next();
            Object keyObject = entry.getKey();
            if (keyObject.getClass().getSimpleName().equals(sampleName)) {
                Method method = entry.getValue();
                try {
                    /***
                     这里可能还有bug.就是keyObject是Activity或者Fragment时,
                     退出这些组件后如果再调用下面的代码,就有可能报异常.
                     */
                    if (method != null) {
                        /*Log.e(TAG, "EventBus dispatchEvent()keyObject: " + keyObject
                                + " what: " + what
                                + " objArray: " + objArray);*/
                        return method.invoke(keyObject, what, objArray);
                    }
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "EventBus dispatchEvent() : IllegalAccessException");
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    Log.e(TAG, "EventBus dispatchEvent() : InvocationTargetException");
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG, "EventBus dispatchEvent() : Exception");
                    e.printStackTrace();
                }
                break;
            }
        }

        return null;
    }

    /***
     * 如果知道要调用"onEvent"方法的对象,
     * 那么调用就更加简单.
     * @param object
     * @param what
     * @param objArray
     * @return
     */
    private Object dispatchEvent(Object object, int what, Object[] objArray) {
        if (object == null) {
            return null;
        }

        Method method = mObjectMethodMap.get(object);
        try {
            /***
             这里可能还有bug.就是keyObject是Activity或者Fragment时,
             退出这些组件后如果再调用下面的代码,就有可能报异常.
             */
            if (method != null) {
                return method.invoke(object, what, objArray);
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "EventBus dispatchEvent() : IllegalAccessException");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            Log.e(TAG, "EventBus dispatchEvent() : InvocationTargetException");
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "EventBus dispatchEvent() : Exception");
            e.printStackTrace();
        }

        return null;
    }

    private void threadHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        Object object = msg.obj;
        if (object == null) {
            return;
        }

        Object[] objArray = mThreadMsgMap.get(msg);
        if (object instanceof Class) {
            dispatchEvent((Class) msg.obj, msg.what, objArray);
        } else {
            dispatchEvent(msg.obj, msg.what, objArray);
        }

        synchronized (mThreadMsgMap) {
            mThreadMsgMap.remove(msg);
        }
    }

    private void uiHandleMessage(Message msg) {
        if (msg == null) {
            return;
        }

        Object object = msg.obj;
        if (object == null) {
            return;
        }

        Object[] objArray = mUiMsgMap.get(msg);
        if (object instanceof Class) {
            dispatchEvent((Class) msg.obj, msg.what, objArray);
        } else {
            dispatchEvent(msg.obj, msg.what, objArray);
        }

        synchronized (mUiMsgMap) {
            mUiMsgMap.remove(msg);
        }
    }

}
