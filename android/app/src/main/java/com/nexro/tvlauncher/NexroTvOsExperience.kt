package com.nexro.tvlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher

/** Native, remote-first tvOS-inspired launcher. It shows real installed apps only. */
class NexroTvOsExperience(private val a: Activity, private val remoteSettings: () -> Unit) {
    private val p = a.getSharedPreferences("nexro_tv_preferences", 0)
    private val bg = Color.rgb(5,7,12); private val white = Color.rgb(247,248,252)
    private val muted = Color.rgb(163,172,191); private val panel = Color.rgb(19,24,36)
    private val focus = Color.rgb(48,59,81); private val blue = Color.rgb(126,158,235)
    private val apps = mutableListOf<Item>(); private val fav = mutableSetOf<String>()
    private var page = "Home"
    private val tabs = listOf("Home","Movies","TV Shows","Apps","Library","Search","Settings")
    private data class Item(val name:String,val pkg:String,val icon:android.graphics.drawable.Drawable,val system:Boolean)

    fun render() { loadApps(); fav.addAll(p.getStringSet("favorites", emptySet()) ?: emptySet()); show("Home") }

    private fun loadApps() {
        apps.clear(); val pm=a.packageManager; val seen=mutableSetOf<String>()
        val qs=listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        qs.flatMap { try { pm.queryIntentActivities(it,PackageManager.MATCH_ALL) } catch (_:Exception) { emptyList() } }.forEach { ri ->
            val pkg=ri.activityInfo.packageName
            if(pkg!=a.packageName && seen.add(pkg) && pm.getLaunchIntentForPackage(pkg)!=null) {
                val ai=ri.activityInfo.applicationInfo
                apps.add(Item(ri.loadLabel(pm).toString(),pkg,try{pm.getApplicationIcon(pkg)}catch(_:Exception){a.applicationInfo.loadIcon(pm)},(ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0))
            }
        }
        apps.sortBy { it.name.lowercase() }
    }

    private fun show(dest:String, query:String="") {
        page=dest
        val root=LinearLayout(a).apply { orientation=1;setPadding(40,16,40,18);setBackgroundColor(bg);isFocusable=true;isFocusableInTouchMode=true }
        val top=LinearLayout(a).apply { orientation=0;gravity=Gravity.CENTER_VERTICAL }
        top.addView(txt("nexro",29f,white,true));top.addView(txt(" TV",17f,muted))
        top.addView(View(a),LinearLayout.LayoutParams(0,1,1f))
        tabs.forEach { t ->
            val selected=t==dest
            top.addView(txt(t,13f,if(selected)white else muted,selected).apply {
                setPadding(12,10,12,10);if(selected)background=shape(Color.rgb(39,46,62),22)
                isFocusable=true;setOnFocusChangeListener{v,f->v.background=if(f||selected)shape(if(f)focus else Color.rgb(39,46,62),22) else null;v.animate().scaleX(if(f)1.04f else 1f).scaleY(if(f)1.04f else 1f).setDuration(duration()).start()}
                setOnClickListener{show(t)}
            })
        }
        root.addView(top,LinearLayout.LayoutParams(-1,52))
        when(dest) {
            "Home"->home(root)
            "Apps"->appGrid(root,apps)
            "Movies","TV Shows"->mediaEmpty(root,dest)
            "Library"->library(root)
            "Search"->search(root,query)
            "Settings"->settings(root)
        }
        root.addView(txt("NEXRO  ·  MADE FOR THE BIG SCREEN",10f,Color.rgb(94,104,124),true).apply{gravity=Gravity.CENTER;setPadding(0,5,0,0)},LinearLayout.LayoutParams(-1,22))
        a.setContentView(root);root.requestFocus()
    }

    private fun home(root:LinearLayout) {
        val hero=LinearLayout(a).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(30,20,22,20);background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(39,51,82),Color.rgb(22,30,50),Color.rgb(12,16,27))).apply{cornerRadius=28f}}
        val copy=LinearLayout(a).apply{orientation=1;gravity=Gravity.CENTER_VERTICAL}
        copy.addView(txt("N E X R O   E N T E R T A I N M E N T",10f,Color.rgb(203,215,250),true))
        copy.addView(txt("Your screen.\nYour next favourite.",35f,white,true).apply{setPadding(0,10,0,8);setLineSpacing(-2f,1f)})
        copy.addView(txt("Discover the apps already installed on your TV.",14f,Color.rgb(211,217,232)))
        val actions=LinearLayout(a).apply{orientation=0;setPadding(0,16,0,0)}
        actions.addView(button("Explore apps",true){show("Apps")})
        actions.addView(button("Search",false){show("Search")}.apply{setPadding(18,12,18,12)})
        copy.addView(actions);hero.addView(copy,LinearLayout.LayoutParams(0,-1,1.3f))
        val art=LinearLayout(a).apply{gravity=Gravity.CENTER;background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(111,139,203),Color.rgb(48,65,110),Color.rgb(19,27,48))).apply{cornerRadius=24f}}
        if(apps.isNotEmpty())art.addView(ImageView(a).apply{setImageDrawable(apps[0].icon);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(112,112)) else art.addView(txt("N",90f,white,true))
        hero.addView(art,LinearLayout.LayoutParams(0,-1,.55f).apply{leftMargin=20})
        root.addView(hero,LinearLayout.LayoutParams(-1,218).apply{topMargin=12})
        shelf(root,"Continue Watching","No playback history is available yet. Open a streaming app to get started.",emptyList())
        shelf(root,"Your Apps","Installed on this TV",apps)
        val favorites=apps.filter{fav.contains(it.pkg)}
        if(favorites.isNotEmpty())shelf(root,"Favorites","Saved apps",favorites)
    }

    private fun shelf(root:LinearLayout,title:String,subtitle:String,items:List<Item>) {
        root.addView(txt(title,21f,white,true),LinearLayout.LayoutParams(-1,31).apply{topMargin=12})
        root.addView(txt(subtitle,12f,muted),LinearLayout.LayoutParams(-1,20))
        val sc=HorizontalScrollView(a).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false;setPadding(4,8,4,8)}
        val row=LinearLayout(a).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
        if(items.isEmpty())row.addView(txt(subtitle,13f,muted).apply{setPadding(10,15,10,15)})
        else items.forEach{row.addView(card(it,158),LinearLayout.LayoutParams(158,139).apply{marginEnd=15})}
        sc.addView(row);root.addView(sc,LinearLayout.LayoutParams(-1,153))
    }

    private fun appGrid(root:LinearLayout,items:List<Item>) {
        root.addView(txt("Apps",30f,white,true),LinearLayout.LayoutParams(-1,42).apply{topMargin=15})
        root.addView(txt("Real applications installed on this TV",13f,muted),LinearLayout.LayoutParams(-1,25))
        if(items.isEmpty()){empty(root,"No launchable apps found","Install a TV-compatible app and Nexro will discover it automatically.");return}
        val sc=ScrollView(a).apply{isVerticalScrollBarEnabled=false}
        val col=LinearLayout(a).apply{orientation=1}
        items.chunked(5).forEach{group->
            val row=LinearLayout(a).apply{orientation=0}
            group.forEach{row.addView(card(it,148),LinearLayout.LayoutParams(0,143,1f).apply{marginEnd=12})}
            repeat(5-group.size){row.addView(View(a),LinearLayout.LayoutParams(0,1,1f).apply{marginEnd=12})}
            col.addView(row,LinearLayout.LayoutParams(-1,153))
        }
        sc.addView(col);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun card(item:Item,width:Int):View = LinearLayout(a).apply {
        orientation=1;gravity=Gravity.CENTER;setPadding(8,8,8,7);background=shape(panel,20,Color.rgb(43,51,68));isFocusable=true;isFocusableInTouchMode=true
        val art=LinearLayout(a).apply{gravity=Gravity.CENTER;background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(38,48,70),Color.rgb(21,27,41))).apply{cornerRadius=18f};addView(ImageView(a).apply{setImageDrawable(item.icon);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription=item.name},LinearLayout.LayoutParams(72,72))}
        addView(art,LinearLayout.LayoutParams(-1,92))
        addView(txt(item.name,12f,white,true).apply{gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(2,8,2,0)},LinearLayout.LayoutParams(-1,-2))
        setOnFocusChangeListener{v,f->v.background=shape(if(f)focus else panel,20,if(f)Color.WHITE else Color.rgb(43,51,68));val s=if(f&&motion())1.07f else 1f;v.animate().scaleX(s).scaleY(s).setDuration(duration()).start()}
        setOnClickListener{launch(item)}
        setOnLongClickListener{details(item);true}
    }

    private fun details(item:Item) {
        val root=LinearLayout(a).apply{orientation=1;setPadding(48,28,48,28);setBackgroundColor(bg)}
        root.addView(button("‹  Back to Apps",false){show("Apps")})
        val row=LinearLayout(a).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(0,28,0,28)}
        row.addView(ImageView(a).apply{setImageDrawable(item.icon)},LinearLayout.LayoutParams(112,112))
        val info=LinearLayout(a).apply{orientation=1;setPadding(24,0,0,0)}
        info.addView(txt(item.name,30f,white,true));info.addView(txt(if(item.system)"System application" else "Installed application",14f,muted).apply{setPadding(0,6,0,0)})
        info.addView(txt(item.pkg,12f,muted).apply{setPadding(0,5,0,0)});row.addView(info);root.addView(row)
        root.addView(button("Open "+item.name,true){launch(item)})
        root.addView(button(if(fav.contains(item.pkg))"Remove from Favorites" else "Add to Favorites",false){
            if(fav.contains(item.pkg))fav.remove(item.pkg)else fav.add(item.pkg)
            p.edit().putStringSet("favorites",fav.toSet()).apply();details(item)
        }.apply{setPadding(17,12,17,12)})
        a.setContentView(root);root.requestFocus()
    }

    private fun mediaEmpty(root:LinearLayout,dest:String) {
        root.addView(txt(dest,30f,white,true),LinearLayout.LayoutParams(-1,42).apply{topMargin=16})
        root.addView(txt("Browse your entertainment",14f,muted))
        empty(root,"No media catalog connected","Nexro does not invent movie listings. Connect a real media provider to populate this screen, or explore installed streaming apps.")
        root.addView(button("Explore installed apps",true){show("Apps")})
    }

    private fun library(root:LinearLayout) {
        root.addView(txt("Library",30f,white,true),LinearLayout.LayoutParams(-1,42).apply{topMargin=16})
        val items=apps.filter{fav.contains(it.pkg)}
        if(items.isEmpty())empty(root,"Your library starts here","Long-press an app tile and save it to Favorites. Saved choices stay on this device.")
        else shelf(root,"Favorites","Saved on this TV",items)
    }

    private fun search(root:LinearLayout,initial:String) {
        root.addView(txt("Search",30f,white,true),LinearLayout.LayoutParams(-1,42).apply{topMargin=15})
        val field=EditText(a).apply{setSingleLine(true);textSize=20f;setTextColor(white);setHintTextColor(muted);hint="Search installed apps";setText(initial);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;setPadding(16,12,16,12);background=shape(Color.rgb(20,25,37),18,Color.rgb(59,70,92));isFocusable=true}
        root.addView(field,LinearLayout.LayoutParams(-1,56).apply{topMargin=10;bottomMargin=10})
        val results=LinearLayout(a).apply{orientation=1}
        fun update(q:String){results.removeAllViews();val found=apps.filter{it.name.contains(q,true)||it.pkg.contains(q,true)}
            if(q.isBlank())results.addView(txt("Search across installed apps.",14f,muted))
            else if(found.isEmpty())results.addView(txt("No results for: "+q,14f,muted))
            else found.chunked(5).forEach{g->val row=LinearLayout(a).apply{orientation=0};g.forEach{row.addView(card(it,148),LinearLayout.LayoutParams(0,143,1f).apply{marginEnd=12})};repeat(5-g.size){row.addView(View(a),LinearLayout.LayoutParams(0,1,1f))};results.addView(row,LinearLayout.LayoutParams(-1,153))}
        }
        field.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,af:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){update(s?.toString().orEmpty())};override fun afterTextChanged(e:Editable?) {}})
        val sc=ScrollView(a).apply{isVerticalScrollBarEnabled=false};sc.addView(results);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));update(initial)
    }

    private fun settings(root:LinearLayout) {
        root.addView(txt("Settings",30f,white,true),LinearLayout.LayoutParams(-1,42).apply{topMargin=15})
        root.addView(txt("Make Nexro feel right for your living room.",13f,muted))
        val list=LinearLayout(a).apply{orientation=1}
        setting(list,"Appearance","Dark cinematic theme","Optimized for TV viewing"){}
        setting(list,"Motion",if(motion())"Animations on" else "Reduced motion","Toggle card focus scaling"){p.edit().putBoolean("motion",!motion()).apply();show("Settings")}
        setting(list,"Phone remote","Optional local-network pairing","Pairing never blocks the launcher"){remoteSettings()}
        setting(list,"Network","Local connection only","The phone remote uses the same Wi-Fi network"){}
        setting(list,"Privacy","Favorites stored locally","Nexro stores preferences on this device"){}
        setting(list,"About Nexro","Version 1.1.0","Native launcher for installed Android applications"){android.app.AlertDialog.Builder(a).setTitle("About Nexro").setMessage("Nexro TV Launcher\nVersion 1.1.0\nMedia catalog: not configured").setPositiveButton("OK",null).show()}
        setting(list,"Reset preferences","Clear favorites and motion settings","Does not uninstall apps or erase Android data"){
            android.app.AlertDialog.Builder(a).setTitle("Reset Nexro?").setMessage("Clear saved favorites and motion preferences?").setNegativeButton("Cancel",null).setPositiveButton("Reset"){_,_->p.edit().clear().apply();fav.clear();show("Settings")}.show()
        }
        val sc=ScrollView(a).apply{isVerticalScrollBarEnabled=false};sc.addView(list);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun setting(parent:LinearLayout,title:String,value:String,desc:String,click:()->Unit) {
        parent.addView(LinearLayout(a).apply{orientation=1;setPadding(17,13,17,13);background=shape(Color.rgb(18,23,34),17);isFocusable=true
            addView(txt(title,16f,white,true));addView(txt(value,12f,blue).apply{setPadding(0,4,0,0)});addView(txt(desc,11f,muted).apply{setPadding(0,4,0,0)})
            setOnFocusChangeListener{v,f->v.background=shape(if(f)focus else Color.rgb(18,23,34),17,if(f)Color.WHITE else null)}
            setOnClickListener{click()}
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})
    }

    private fun empty(root:LinearLayout,title:String,desc:String) {
        val box=LinearLayout(a).apply{orientation=1;gravity=Gravity.CENTER;setPadding(28,20,28,20)}
        box.addView(txt(title,23f,white,true).apply{gravity=Gravity.CENTER})
        box.addView(txt(desc,13f,muted).apply{gravity=Gravity.CENTER;setPadding(0,9,0,0)})
        root.addView(box,LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun launch(item:Item) {
        try { val i=a.packageManager.getLaunchIntentForPackage(item.pkg);if(i!=null)a.startActivity(i) else android.app.AlertDialog.Builder(a).setMessage("This app no longer has a launchable activity.").setPositiveButton("OK",null).show() }
        catch(_:Exception){android.app.AlertDialog.Builder(a).setTitle("Couldn't open app").setMessage("Android couldn't open "+item.name+".").setPositiveButton("OK",null).show()}
    }

    private fun button(label:String,primary:Boolean,click:()->Unit)=txt(label,13f,if(primary)bg else white,true).apply{
        setPadding(17,12,17,12);background=shape(if(primary)Color.WHITE else Color.rgb(34,41,56),24);isFocusable=true
        setOnFocusChangeListener{v,f->if(f)v.background=shape(if(primary)Color.rgb(229,234,246)else focus,24)}
        setOnClickListener{click()}
    }
    private fun txt(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(a).apply{text=s;textSize=size;setTextColor(color);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);includeFontPadding=false}
    private fun shape(c:Int,r:Int,stroke:Int?=null)=GradientDrawable().apply{setColor(c);cornerRadius=r.toFloat();if(stroke!=null)setStroke(1,stroke)}
    private fun motion()=p.getBoolean("motion",true)
    private fun duration()=if(motion())170L else 0L
}
