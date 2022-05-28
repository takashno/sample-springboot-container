# Springboot Application Container Running Sample

## Overview

SpringbootアプリケーションをAWS ECS で常駐プロセスとして稼働した場合の運用課題などを検討してみる。


## Actuator

### 管理Portの分離

SpringBoot Actuator のエンドポイントは一般公開すべきでない情報を得たり、アプリケーションを操作できるものまで多岐にわたる。
そのため、外部から叩かれたくないようなエンドポイントを公開する場合には、管理ポートを変更する場合がある。

```properties
management.server.port=9090
```

上記のように設定することで、Actuator のベースURLは以下となる。

http://localhost:9090/actuator

### Heap Dump

SpringBoot Actuator には、Heap Dump を取得するエンドポイントが用意されている。
こちらを使い、Heap Dump を得る。

```properties
management.endpoints.web.exposure.include=heapdump
# 上記は他を割愛しているだけで、以下などが実運用
# management.endpoints.web.exposure.include=env,health,heapdump
```

#### 実行方法

http://localhost:9090/actuator/heapdump

上記URLを実行することで、heapdump がダウンロードされる。

#### 確認方法

いつだかのJavaまでは標準でついてきていた、VisualVMなどを用いて確認が行える。

https://visualvm.github.io/

#### 運用方法

OutOfMemoryErrorが発生した場合に、Heap Dump を取得したいことがほとんど。
起動時のJVMオプションとしては以下。

```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/heapdump/
```

これをAWSで実行で運用時に実施したい。  
方法はいくつかありそうだが、以下案。

1. CloudWatch Logs を監視
2. サブスクリプションフィルターを使用して java.lang.OutOfMemoryError を監視
3. Lambdaを実行
4. Actuator の heapdump エンドポイントを実行
5. ダウンロード結果をS3にアップロード
6. Slack通知

Slack通知には、  
heapdumpの所在のみではなく発生したLogGroup/LogStreamを含めておかないと不便。


### Thread Dump

SpringBoot Actuator には、Thead Dump を取得するエンドポイントが用意されている。
こちらを使い、Thead Dump を得る。

```properties
management.endpoints.web.exposure.include=threaddump
# 上記は他を割愛しているだけで、以下などが実運用
# management.endpoints.web.exposure.include=env,health,threaddump
```

#### 実行方法

http://localhost:9090/actuator/threadump

上記URLを実行することで、threaddmup がレスポンスとして返却される。


### メトリクス情報

SpringBoot Actuator には、OSやJVM関連のメトリクス値など を取得するエンドポイントが用意されている。
こちらを使い、メトリクスを を得ることができる。

```properties
management.endpoints.web.exposure.include=metrics
# 上記は他を割愛しているだけで、以下などが実運用
# management.endpoints.web.exposure.include=env,health,metrics
```

#### 実行方法

２段階で調べるのがわかりやすい。
まずは、どのようなメトリクス値が取得可能か調べる。

http://localhost:9090/actuator/metrics

```json
{
	"names": [
		"application.ready.time",
		"application.started.time",
		"disk.free",
		"disk.total",
		"executor.active",
		"executor.completed",
		"executor.pool.core",
		"executor.pool.max",
		"executor.pool.size",
		"executor.queue.remaining",
		"executor.queued",
		"jvm.buffer.count",
		"jvm.buffer.memory.used",
		"jvm.buffer.total.capacity",
		"jvm.classes.loaded",
		"jvm.classes.unloaded",
		"jvm.gc.live.data.size",
		"jvm.gc.max.data.size",
		"jvm.gc.memory.allocated",
		"jvm.gc.memory.promoted",
		"jvm.gc.overhead",
		"jvm.gc.pause",
		"jvm.memory.committed",
		"jvm.memory.max",
		"jvm.memory.usage.after.gc",
		"jvm.memory.used",
		"jvm.threads.daemon",
		"jvm.threads.live",
		"jvm.threads.peak",
		"jvm.threads.states",
		"logback.events",
		"process.cpu.usage",
		"process.files.max",
		"process.files.open",
		"process.start.time",
		"process.uptime",
		"system.cpu.count",
		"system.cpu.usage",
		"system.load.average.1m",
		"tomcat.sessions.active.current",
		"tomcat.sessions.active.max",
		"tomcat.sessions.alive.max",
		"tomcat.sessions.created",
		"tomcat.sessions.expired",
		"tomcat.sessions.rejected"
	]
}
```

このあと、取得したいメトリクスを指定して値を得る。

http://localhost:9090/actuator/metrics/jvm.gc.memory.allocated

```json
{
	"name": "jvm.gc.memory.allocated",
	"description": "Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next",
	"baseUnit": "bytes",
	"measurements": [
		{
			"statistic": "COUNT",
			"value": 60817408
		}
	],
	"availableTags": []
}
```


### heapdump, threaddump, metrics について

このエンドポイントを定期的に叩く運用は、いつ落ちるか分からないプロセスに対して叩きにいくということなので微妙すぎる。  
必要になったのタイミングで局所的に確認したい場合の利用に止めるべきだと考える。

`metrics` については、監視対象 → 監視サービスへ通知する方が一般的。  
アプリから直接Pushする、エージェント同梱方式、サイドカー方式などいくつか手段はあると考える。


## GC Log のCloudWatchLogs出力

開発や本番環境運用をしているとトラブルシュートのために、GCLogが欲しい場合がある。  
SpringBoot＋Serverless（ECS）で稼働するアプリケーションは、  
極力 [12 Factor App](https://12factor.net/ja/) の思想に沿って組むべきであるから、  
GCLogを別ファイルで出すということは、[ログの思想](https://12factor.net/ja/logs) と合わない部分があると考える。  
なので、なるべくPluggableな形で実現できる方式を考える。

### 案1

- GCLogを標準出力に出す。
- サブスクリプションフィルタでgcのPrefixが付くもののみをフィルタしCloudWatchでフィルタリングする.


### 案2

標準出力＋logbackでのフィルタリング  
おそらくこれはやるべきではない。  
Javaアプリケーションの処理となるので性能劣化に繋がる。


### 案3

NetflixのOSSであるSpectatorの一部のライブラリを使うと、
GCLogに出力するイベントをハンドルできる？らしい。
GCLogの内容だけ、CloudWatchLogsに送信するようなことをしたら良いのでは？と考えたが、
うまく試しきれていない...

- https://github.com/Netflix/spectator/tree/main/spectator-ext-gc
- https://github.com/j256/cloudwatch-logback-appender