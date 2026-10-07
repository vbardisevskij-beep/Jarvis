package com.vitalya.jarvis

import android.app.*
import android.content.*
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.*
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.*
import android.speech.tts.TextToSpeech
import java.net.URLEncoder
import java.util.Locale

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
 private lateinit var sr: SpeechRecognizer
 private lateinit var tts: TextToSpeech
 private var armed=false
 private var pendingText:String?=null
 private var pendingApp:String?=null
 private var listening=false
 private var speaking=false
 private var neuralTts: OfflineTts?=null
 private val h=Handler(Looper.getMainLooper())
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate(); channel(); startForeground(7,note()); tts=TextToSpeech(this,this); initNeuralTts(); sr=SpeechRecognizer.createSpeechRecognizer(this); sr.setRecognitionListener(this); listen()}
 override fun onInit(s:Int){
  if(s==TextToSpeech.SUCCESS){
   val uk=Locale("uk","UA")
   tts.language=uk
   val candidates=tts.voices?.filter{ it.locale.language=="uk" || it.locale.language=="ru" }.orEmpty()
   val male=candidates.firstOrNull{
    val n=it.name.lowercase()
    n.contains("male") || n.contains("mascul") || n.contains("чолов")
   }
   if(male!=null) tts.voice=male
   // Keep consonants intelligible: masculine-leaning, not cartoonishly low.
   tts.setPitch(0.92f)
   tts.setSpeechRate(0.94f)
  }
 }
 private fun channel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("jarvis","JARVIS",NotificationManager.IMPORTANCE_LOW))}
 private fun note()=Notification.Builder(this,"jarvis").setContentTitle("JARVIS активний").setContentText("Слухаю слово «Джарвіс»").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
 private fun listen(){if(listening||speaking)return; listening=true; h.postDelayed({try{sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE,"uk-UA");putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)})}catch(_:Exception){listening=false;h.postDelayed({listen()},800)}},500)}
 private fun initNeuralTts(){
  try{
   val dir=filesDir.resolve("voice");dir.mkdirs()
   val names=listOf("duration_predictor.int8.onnx","text_encoder.int8.onnx","vector_estimator.int8.onnx","vocoder.int8.onnx","tts.json","unicode_indexer.bin","voice.bin")
   names.forEach{n->val out=dir.resolve(n);if(!out.exists())assets.open("voice/"+n).use{input->out.outputStream().use{input.copyTo(it)}}}
   val st=OfflineTtsSupertonicModelConfig(durationPredictor=dir.resolve(names[0]).path,textEncoder=dir.resolve(names[1]).path,vectorEstimator=dir.resolve(names[2]).path,vocoder=dir.resolve(names[3]).path,ttsJson=dir.resolve(names[4]).path,unicodeIndexer=dir.resolve(names[5]).path,voiceStyle=dir.resolve(names[6]).path)
   val model=OfflineTtsModelConfig(supertonic=st,numThreads=2,debug=false,provider="cpu")
   neuralTts=OfflineTts(config=OfflineTtsConfig(model=model))
  }catch(_:Throwable){neuralTts=null}
 }
 private fun say(x:String){
  speaking=true;listening=false;try{sr.cancel()}catch(_:Exception){}
  val nt=neuralTts
  if(nt!=null){
   Thread{
    try{
     val gc=GenerationConfig(sid=0,speed=0.95f,numSteps=8,extra=mapOf("lang" to "uk"))
     val audio=nt.generateWithConfig(text=x, config=gc)
     val samples=audio.samples
     val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(samples.size*4).setTransferMode(AudioTrack.MODE_STATIC).build()
     track.write(samples,0,samples.size,AudioTrack.WRITE_BLOCKING);track.play()
     Thread.sleep((samples.size*1000L/audio.sampleRate)+250);track.stop();track.release();h.post{speaking=false;listen()}
    }catch(_:Throwable){h.post{tts.speak(x,TextToSpeech.QUEUE_FLUSH,null,"j");h.postDelayed({speaking=false;listen()},2200)}}
   }.start()
  }else{tts.speak(x,TextToSpeech.QUEUE_FLUSH,null,"j");h.postDelayed({speaking=false;listen()},2200)}
 }
 private fun process(raw:String){
  var s=raw.lowercase().trim()
  if(!armed&&(s.contains("джарвіс")||s.contains("джарвис"))){armed=true;s=s.replace("джарвіс","").replace("джарвис","").trim();if(s.isBlank()){say("Слухаю, Віталік");return}}
  if(!armed)return
  if(pendingText!=null){
   if(s.contains("відправ")||s.contains("отправ")||s=="так"){share();return}
   if(s.contains("скасуй")||s.contains("отмена")||s=="ні"){pendingText=null;pendingApp=null;say("Скасував");return}
  }
  when{
   s.contains("подзвони")||s.contains("позвони")->call(s.replace("подзвони","").replace("позвони","").trim())
   s.contains("напиши")->message(s)
   s.contains("тікток")||s.contains("tiktok")||s.contains("тик ток")->tiktok(s)
   s.contains("ліхтар")||s.contains("фонар")->torch(!(s.contains("вимк")||s.contains("выкл")))
   s.contains("таймер")->timer(s)
   s.contains("будильник")->alarm(s)
   s.contains("чатгпт")||s.contains("chatgpt")->launch("com.openai.chatgpt")
   s.contains("ютуб")||s.contains("youtube")->{say("Відкриваю YouTube");h.postDelayed({launch("com.google.android.youtube")},650)}
   s.contains("відкрий")||s.contains("открой")||s.contains("включи")||s.contains("увімкни")->openApp(s.replace("відкрий","").replace("открой","").replace("включи","").replace("увімкни","").trim())
   else->{armed=false;say(listOf("Не розібрав команду. Скажи ще раз, Віталік.","Бляха, не розчув. Поклич мене ще раз.","От зараза, не зрозумів. Скажи: Джарвіс, і команду.").random())}
  }
 }
 private fun call(name:String){
  val c:Cursor?=contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" LIKE ?",arrayOf("%"+name+"%"),null)
  c.use{if(it!=null&&it.moveToFirst()){val n=it.getString(1);val d=it.getString(0);say("Телефоную "+d);h.postDelayed({try{startActivity(Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));armed=false}catch(_:Exception){}},1200)}else say("Не знайшов "+name+" у контактах")}
 }
 private fun message(s:String){
  val app=if(s.contains("viber")||s.contains("вайбер"))"com.viber.voip" else if(s.contains("telegram")||s.contains("телеграм"))"org.telegram.messenger" else {say("Через Telegram чи Viber?");return}
  val i=s.indexOf(':');if(i<0){say("Скажи текст після двокрапки");return};pendingText=s.substring(i+1).trim();pendingApp=app;say("Повідомлення: "+pendingText+". Відправити?")
 }
 private fun share(){val x=pendingText?:return;val p=pendingApp?:return;pendingText=null;pendingApp=null;try{startActivity(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,x);setPackage(p);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)});armed=false}catch(_:Exception){say("Не вдалося відкрити месенджер")}}
 private fun tiktok(s:String){val q=s.replace("тікток","").replace("tiktok","").replace("тик ток","").replace("знайди","").replace("відео","").trim();val u=if(q.isBlank())"https://www.tiktok.com/foryou" else "https://www.tiktok.com/search?q="+URLEncoder.encode(q,"UTF-8");try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)).apply{setPackage("com.zhiliaoapp.musically");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)});armed=false}catch(_:Exception){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
 private fun torch(on:Boolean){try{val cm=getSystemService(CameraManager::class.java);cm.setTorchMode(cm.cameraIdList.first(),on);say(if(on)"Ліхтарик увімкнув" else "Ліхтарик вимкнув")}catch(_:Exception){say("Бляха, з ліхтариком не вийшло")}}
 private fun openApp(n:String){val pm=packageManager;val a=pm.getInstalledApplications(0).firstOrNull{pm.getApplicationLabel(it).toString().lowercase().contains(n)};val i=a?.let{pm.getLaunchIntentForPackage(it.packageName)};if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);armed=false}else say("Не знайшов програму "+n)}
 private fun launch(p:String){val i=packageManager.getLaunchIntentForPackage(p);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);armed=false}else say("Програма не встановлена")}
 private fun num(s:String)=Regex("\\d+").find(s)?.value?.toIntOrNull()
 private fun timer(s:String){val n=num(s)?:return say("На скільки хвилин?");val sec=if(s.contains("год"))n*3600 else n*60;startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,sec).putExtra(AlarmClock.EXTRA_SKIP_UI,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));say("Таймер поставив")}
 private fun alarm(s:String){val m=Regex("(\\d{1,2})[:.](\\d{2})").find(s);val hr=m?.groupValues?.get(1)?.toIntOrNull()?:num(s)?:return say("Скажи час");val mn=m?.groupValues?.get(2)?.toIntOrNull()?:0;startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,hr).putExtra(AlarmClock.EXTRA_MINUTES,mn).putExtra(AlarmClock.EXTRA_SKIP_UI,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));say("Будильник поставив")}
 override fun onResults(b:Bundle?){listening=false;b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{process(it)};if(!speaking)h.postDelayed({listen()},900)}
 override fun onError(e:Int){listening=false;if(!speaking)h.postDelayed({listen()},1000)}
 override fun onReadyForSpeech(p:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(r:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onEndOfSpeech(){};override fun onPartialResults(b:Bundle?){};override fun onEvent(e:Int,p:Bundle?){}
 override fun onDestroy(){try{sr.destroy()}catch(_:Exception){};try{neuralTts?.release()}catch(_:Throwable){};tts.shutdown();super.onDestroy()}
}
