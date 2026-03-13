/*
 * Copyright 2012 Jun Ohtani
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
package lucene.gosen.test.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;

public class ComponentContainer {
  
  private File[] targetJarFiles;
  private URL[] targetJarUrls;
  
  private RestrictedURLClassLoader urlClassLoader;
  
  public ComponentContainer(File[] targetJarFiles){
    this.targetJarFiles = targetJarFiles;
  }
  
  private Class findComponent(String targetClass)throws ClassNotFoundException{
    ClassLoader ctxClsLoader = Thread.currentThread().getContextClassLoader();
    if(targetJarFiles != null){

      if(targetJarUrls == null){
        targetJarUrls = new URL[targetJarFiles.length];
        try{
          for(int i=0;i<targetJarFiles.length;i++){
            targetJarUrls[i] = toUrl(targetJarFiles[i]);
          }
        }catch(MalformedURLException mue){
          throw new RuntimeException("Filt toUrl convert fail!");
        }
      }
      if(urlClassLoader == null){
        synchronized (this){
          urlClassLoader = new RestrictedURLClassLoader(targetJarUrls, ctxClsLoader);
        }
      }
      Class cls = urlClassLoader.loadClass(targetClass);
      return cls;
    }
    throw new ClassNotFoundException("Not Found "+targetClass+"");
  }
  
  public Class loadComponent(String targetClass)throws ClassNotFoundException{
    Class cls = findComponent(targetClass);
    if(cls != null){
      return cls;
    }
    throw new ClassNotFoundException("Not Found "+targetClass+"");
  }
  
  private URL toUrl(File file) throws MalformedURLException {
    String filePath = file.getAbsolutePath();
    filePath = filePath.replace('\\', '/');
    if (filePath.charAt(0) != '/') filePath = "/" + filePath;
    if (file.isDirectory()) filePath = filePath + "/";
    URL url = new URL("file", null, filePath);
    return url;
  }
  
  /**
   * TODO 引数にnullが渡せる場合にうまくいかない？コンストラクタ引数のクラス配列は別途与えたほうがいいか？
   * @param targetClass
   * @param args
   * @return
   */
  public Object createComponent(String targetClass, Class[] argTypes, Object[] args)throws ClassNotFoundException{
    Class cls = loadComponent(targetClass);
    
    try{
      Constructor constructor = cls.getConstructor(argTypes);
      Object obj = constructor.newInstance(args);
      return obj;
    }catch(Exception e){
      throw new RuntimeException("Unable to instantiate target["+targetClass+"]");
    }
  }
}
