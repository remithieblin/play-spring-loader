# Play Spring Loader


This is a application loader for Play applications that runs with Spring as the DI.

The project targets Play 2.5.4

To use in your Play application project:

1. add the dependency in your build.sbt file: "com.actimust"% "play-spring-loader" % "1.0-SNAPSHOT"
2. configure the loader in the conf file: play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"
3. You can exclude bindings. This exclude is needed (see below for explanation): play.bindings.disabled += "play.api.libs.Crypto"


The library is hosted on Sonatype. 


Example config for `scala` based app:

```sh
play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"

play.modules.enabled += "com.demo.spring.MyModule"

play.spring.configs += "config.AppConfig"
play.spring.configs += "play.api.spring.CoreConfig"
play.bindings.disabled += "play.api.libs.Crypto"
```

with:

```scala
package config

import org.springframework.context.annotation.{ComponentScan, Configuration}

@Configuration
@ComponentScan(Array("com.demo.spring", "controllers"))
class AppConfig  {

}
```

Example config for `java` based app:

```sh
play.application.loader = "com.actimust.play.spring.SpringApplicationLoader"

play.modules.enabled += "com.demo.spring.MyModule"

# Required as workaround, see spring-application-loader docs
play.bindings.disabled += "play.libs.Crypto"
play.bindings.disabled += "play.api.libs.Crypto"
play.spring.configs = ["com.example.PlaySpringDIConfiguration"]
```

with:

```java
package com.example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class PlaySpringDIConfiguration {

}
```


## Explanation for the Crypto class exclude:

The issue is that spring resolves dependencies by type:

1/ class CSRFTokenSignerProvider @Inject() (@Named("signer") signer: CookieSigner) :
CSRFTokenSignerProvider needs a CookieSigner.

2/ bind[CookieSigner].toProvider[CookieSignerProvider]
=> class HMACSHA1CookieSigner is a CookieSigner

3/ bind[play.api.libs.Crypto].toSelf,  class Crypto @Inject() (signer: CookieSigner, tokenSigner: CSRFTokenSigner, aesCrypter: AESCrypter) extends CookieSigner
=> class Crypto is a CookieSigner

4/ Spring finds these 2 candidates for the CSRFTokenSignerProvider class because they both are of type CookieSigner. So Spring tries to construct both of them.

5/ class Crypto needs a CSRFTokenSigner, which is what Spring was trying to construct in the first place.. : circular dependency.