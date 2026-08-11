package org.aarchdroid.dragonterminal.frontend.web

import android.net.Uri
import java.util.Locale

/**
 * API ac — filtrado de anuncios.
 *
 * Tres capas, inspiradas en Shields (Brave)/uBlock pero implementadas con los
 * APIs del WebView de Android (WebView no soporta extensiones de Chrome):
 *
 * 1. **Bloqueo de red**: [shouldBlock] consulta una lista curada de dominios de
 *    redes publicitarias y patrones de URL. Se usa desde
 *    `shouldInterceptRequest` (corre en un hilo de fondo) y devuelve una
 *    respuesta vacía para que el anuncio no se cargue.
 * 2. **Cosmético**: [cosmeticCss] oculta por CSS los selectores de anuncios
 *    genéricos y de YouTube (overlay, banners, cards promocionadas).
 * 3. **Scriptlet**: [youtubeSkipJs] pulsa el botón "Saltar anuncio" de YouTube
 *    cuando aparece (interval con guard para no duplicarse).
 *
 * Siempre activo, sin UI. Listas deliberadamente conservadoras: si una URL no
 * es claramente publicidad, no se bloquea.
 */
object AcAdBlocker {

    /** Dominios de redes publicitarias/trackers: bloquea el host exacto o subdominios. */
    private val BLOCKED_DOMAINS: Set<String> = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "advertising.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "moatads.com",
        "outbrain.com",
        "taboola.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "lijit.com",
        "quantserve.com",
        "scorecardresearch.com",
        "imrworldwide.com",
        "bluekai.com",
        "addthis.com",
        "addthisedge.com",
        "media.net",
        "chitika.net",
        "revcontent.com",
        "mgid.com"
    )

    /** Subcadenas de alta confianza sobre la URL completa (en minúsculas). */
    private val BLOCKED_SUBSTRINGS: List<String> = listOf(
        "/pagead/",
        "/adsense",
        "/adservice",
        "/adservices",
        "adsbygoogle",
        "ad-slot",
        "doubleclick",
        "googlesyndication",
        "googletagservices",
        "amazon-adsystem",
        "adnxs",
        "criteo",
        "moatads",
        "taboola",
        "outbrain",
        "/gpt/ad",
        "google_ads_iframe"
    )

    /** true si la URL es publicidad y debe devolverse una respuesta vacía. */
    fun shouldBlock(url: String): Boolean {
        if (url.isEmpty()) return false
        val host = Uri.parse(url).host?.lowercase(Locale.ROOT) ?: return false
        for (d in BLOCKED_DOMAINS) {
            if (host == d || host.endsWith(".$d")) return true
        }
        val u = url.lowercase(Locale.ROOT)
        for (s in BLOCKED_SUBSTRINGS) {
            if (u.contains(s)) return true
        }
        return false
    }

    /** CSS cosmético: oculta contenedores de anuncios genéricos y de YouTube. */
    fun cosmeticCss(): String =
        ".advertisement,.ad-container,.adsbox,.ad-slot,ins.adsbygoogle," +
            "[id^=\"google_ads_\"],[id^=\"div-gpt-ad\"]{display:none!important}\n" +
            "#player-ads,#masthead-ad,.ytp-ad-module,.ytp-ad-player-overlay," +
            ".ytp-ad-overlay-container,.ytp-ad-image-overlay,.ytd-display-ad-renderer," +
            ".ytd-promoted-video-renderer,.ytd-in-feed-ad-layout-renderer," +
            ".ytd-banner-promo-renderer,.ytd-ad-slot-renderer{display:none!important}"

    /** true si el host es YouTube (www/m/music/tv/studio). */
    fun isYouTubeHost(host: String?): Boolean =
        host == "youtube.com" || (host != null && host.endsWith(".youtube.com"))

    /**
     * Reemplazos de texto sobre el HTML de YouTube (uBlock `replace`): renombra
     * las claves de publicidad del player/feed a `no_ads`, que el player ignora.
     * Mata la respuesta embebida (`ytInitialPlayerResponse`/`ytInitialData`) sin
     * depender del timing de la inyección.
     */
    fun youtubeHtmlReplacements(html: String): String =
        html.replace("\"adPlacements\"", "\"no_ads\"")
            .replace("\"playerAds\"", "\"no_ads\"")
            .replace("\"adSlots\"", "\"no_ads\"")

    /**
     * Scriptlet document-start (poda de respuesta, estilo uBlock/Brave):
     * intercepta JSON.parse/fetch/XHR y elimina las rutas de anuncios del
     * playerResponse ANTES de que el player las consuma. Así el anuncio nunca
     * se agenda y YouTube no ve ningún request raro (indetectable).
     */
    fun youtubePruneJs(): String = """
        (function(){
        if(window.__acPrune)return;window.__acPrune=1;
        try{
        function pruneObject(obj){
          if(typeof obj!=='object'||obj===null)return;
          (function walk(o){
            if(typeof o!=='object'||o===null)return;
            delete o.adPlacements;delete o.playerAds;delete o.adSlots;delete o.adBreakHeartbeatParams;
            if(o.playerResponse&&typeof o.playerResponse==='object'){
              delete o.playerResponse.adPlacements;delete o.playerResponse.playerAds;delete o.playerResponse.adSlots;
            }
            for(var k in o){try{walk(o[k]);}catch(e){}}
          })(obj);
        }
        JSON.parse=new Proxy(JSON.parse,{apply:function(t,th,a){var o=Reflect.apply(t,th,a);pruneObject(o);return o;}});
        var fetchRE=/player\?|get_watch|^\W+\$/;
        Object.defineProperty(window,'fetch',{value:new Proxy(window.fetch,{apply:function(t,th,a){
          var url='';if(typeof a[0]==='string')url=a[0];else if(a[0]&&a[0].url)url=a[0].url;
          if(!fetchRE.test(url))return Reflect.apply(t,th,a);
          return Reflect.apply(t,th,a).then(function(r){
            try{
              return r.clone().json().then(function(o){
                pruneObject(o);
                var nr=Response.json(o,{status:r.status,statusText:r.statusText,headers:r.headers});
                try{Object.defineProperties(nr,{ok:{value:r.ok},redirected:{value:r.redirected},type:{value:r.type},url:{value:r.url}});}catch(e){}
                return nr;
              });
            }catch(e){return r;}
          });
        }}),configurable:true,writable:true});
        var xhrRE=/\/player(?:\?.+)?\$/;
        var XHRs=new WeakMap();
        var YTXHR=class extends XMLHttpRequest{
          open(method,url){
            if(xhrRE.test(url))XHRs.set(this,{lastRL:undefined,cached:undefined});
            return super.open.apply(this,arguments);
          }
          get response(){
            var inner=super.response;var d=XHRs.get(this);if(!d)return inner;
            var rl=typeof inner==='string'?inner.length:undefined;
            if(d.lastRL!==rl){d.cached=undefined;d.lastRL=rl;}
            if(d.cached!==undefined)return d.cached;
            var o;
            if(typeof inner==='object')o=inner;
            else if(typeof inner==='string'){try{o=JSON.parse(inner);}catch(e){}}
            if(typeof o!=='object')return(d.cached=inner);
            pruneObject(o);
            return(d.cached=typeof inner==='string'?JSON.stringify(o):o);
          }
          get responseText(){var r=this.response;return typeof r!=='string'?super.responseText:r;}
        };
        Object.defineProperty(window,'XMLHttpRequest',{value:YTXHR,configurable:true,writable:true});
        Object.defineProperty(window,'setTimeout',{value:new Proxy(window.setTimeout,{apply:function(t,th,a){if(a[1]===17000)a[1]=17;return Reflect.apply(t,th,a);}}),configurable:true,writable:true});
        try{Object.defineProperty(window,'PrePl',{value:true,configurable:false,writable:false});}catch(e){}
        }catch(e){}
        })();
    """.trimIndent()

    /** Script de poda listo para inyectar en el `<head>` (document-start). */
    fun youtubePruneScriptTag(): String =
        "<script id=\"ac-yt-prune\">" + youtubePruneJs() + "</script>"

    /** Scriptlet: pulsa el botón "Saltar" de YouTube cada 400ms si aparece. */
    fun youtubeSkipJs(): String =
        "(function(){if(window.__acAds)return;window.__acAds=1;" +
            "setInterval(function(){" +
            "var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button');" +
            "if(b){b.disabled=false;b.click();}" +
            "},400);})();"
}
