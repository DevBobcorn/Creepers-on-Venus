<!-- Use preprocessors via the lang attribute! e.g. <template lang="pug"> -->
<template>
  <div id="app">
    <canvas id="background" ref="background"></canvas>
    <audio id="music" ref="music" controls="controls"></audio>
    <div id="audioplate"></div>
    <h1>{{ pagetitle }}</h1>
    <transition name="fade">
      <h2 id="respaku" v-if="records.length > 0" @click="generateResPack()">
        Res Pack
      </h2>
    </transition>
    <transition name="fade">
      <h2 id="datpaku" v-if="records.length > 0" @click="generateDatPack()">
        Dat Pack
      </h2>
    </transition>
    <transition name="fade">
      <table v-if="records.length > 0">
        <thead>
          <tr>
            <th width="5%">#</th>
            <th width="3%">⊙</th>
            <th width="23%">Id</th>
            <th width="23%">Name</th>
            <th width="20%">Artist</th>
            <th width="10%">Length</th>
            <th width="10%">Lyrics</th>
            <th width="3%">▷</th>
            <th width="3%">X</th>
          </tr>
        </thead>
        <tbody>
          <transition-group name="list">
            <tr v-for="(record, index) in records" v-bind:key="index">
              <td width="5%">{{ index + indexstart }}</td>
              <td width="3%">
                <img
                  class="icon"
                  v-bind:src="record.icon"
                  @click="selectIcon(record.id)"
                  title="Select Icon"
                />
              </td>
              <td width="23%"><input v-model="record.id" /></td>
              <td width="23%"><input v-model="record.name" /></td>
              <td width="20%"><input v-model="record.artist" /></td>
              <td width="10%">{{ record.length }}</td>
              <td width="10%"><input v-model="record.lyrics" /></td>
              <td width="3%">
                <p
                  class="icon"
                  @click="playRecord(record.id)"
                  v-bind:title="'Play ' + record.name"
                >
                  ▶
                </p>
              </td>
              <td width="3%">
                <p
                  class="icon"
                  @click="removeRecord(record.id)"
                  v-bind:title="'Delete ' + record.name"
                >
                  ✘
                </p>
              </td>
            </tr>
          </transition-group>
        </tbody>
      </table>
    </transition>
    <transition name="fade">
      <div
        v-if="records.length == 0"
        @dragenter="dragareatxt = 'Drop \'em!'"
        @dragleave="dragareatxt = 'Drag & Drop .ogg/.txt Files'"
        @dragover="allowDrop($event)"
        @drop="
          getFile($event);
          dragareatxt = 'Add More!';
        "
        id="dragarea"
      >
        {{ dragareatxt }}
      </div>
    </transition>
    <transition name="fade">
      <div
        v-if="records.length > 0"
        @dragenter="dragareatxt = 'Drop \'em!'"
        @dragleave="dragareatxt = 'Add More!'"
        @dragover="allowDrop($event)"
        @drop="
          getFile($event);
          dragareatxt = 'Add More!';
        "
        id="dragarea2"
      >
        {{ dragareatxt }}
      </div>
    </transition>
    <span style="position: absolute; bottom: 10px" v-if="records.length > 0">
      <p class="display" id="playtick">Play Tick: {{ playtick }}</p>
      <p class="display" id="lyrictxt">
        {{ lyrictxt }}
      </p>
      <p class="display" id="trslntxt">
        {{ trslntxt }}
      </p>
    </span>
  </div>
</template>

<script>
var playing = false;

var context;
var ctx;
var canvas;
var soundSrc;

var analyser;
var bufferLength;
var dataArray;

const FFT_SIZE = 512;

var WIDTH = 0;
var HEIGHT = 0;
var barWidth = 0;
var barHeight = 0;

function dataURLtoFile(dataurl, filename) {
  var arr = dataurl.split(","),
    mime = arr[0].match(/:(.*?);/)[1],
    bstr = atob(arr[1]),
    n = bstr.length,
    u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  return new File([u8arr], filename, { type: mime });
}

function getNames(str) {
  // Music Name / Artist Name
  var idx = str.lastIndexOf("-");
  if (idx >= 0)
    return [str.substring(idx + 1).trim(), str.substring(0, idx).trim()];
  else return [str, "Unknown"];
}

const DEFAULT_ICONS = [
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAj0lEQVQ4y92S0QnAIBBDHcIZnOAGcAW3cmtLhEg8tWA/exAqmLw7tSH8s1JKTXUdyjm3UkoX1oQcgQwxWGsdAIXga2YzxHdkIMY4hBA9OlEvGNgdRkhDMKsHEHomiO+iE+g+NQC6qRBIu3jfcQIeQ01Y00ctL7EbVQFL11PtuiCsd3P9Z/IV/L1cQz6H3+oBoXO1OSVtTVgAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAeElEQVQ4T92Ryw3AIAxDWcEjcPEcrMD23SY9GeWDaK9tJAsk8p6U0No/q/duOU9MgMYYJUfZDppzrlN3L9vCvtmutpKlQSKBfxRI0lRZUgRF4mo3ShCQLPP7HeT9kIx7AGAkVzIgSAFQfwKAKb7ZQ0qBT7LX0HfrBhBUzmu87GZiAAAAAElFTkSuQmCC",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAeklEQVR42mNgGJ5AUVHxPzomSZODgwMGxmsYNk2BgYFwGsZGNgyrZmTFj0Ks4RjdUBRDYAYgS8I0qqmp4TQEwwBchsA0o3sFxQCQTej+Rw4D9PABqUcJB0FBQbAgDGOLBWR5kHqMmAAJwjCyYmRNMEwwTSArJlrT0AUA6CDEsZxtybIAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAgElEQVR42mNgGJ5AUVHxPzomSZODgwMGxmsYNk2BgYFwGsZGNgyrZmTFPv8Z4BjdUBRDYAYgS4I0yWcw/FdTUwPT2AzBMACbIcguQPcKigEgm9D9jxwG6OEDUo8SDoKCgmBBGMYWC8jyIPUYMQEShGFkxciaYJhgmkBWTLSmoQsAWjLBrfzuqzsAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAfklEQVQ4T92Q0QnAIAxEQ+kCjuCPc7iCE3V0S4ST5Ay2v23gUDTviRH5Z+WcO+eJcVCtdclWFkGttblib2UhbJu7nP2SY4SlTgKBvVRIBaWUKWLJIogkEEVfcQJ9if9vZ8Dz0X43h5TSOEQYAIRov3DpIWKbLYQs8E72Gvpu3fu8vVlFzt4bAAAAAElFTkSuQmCC",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAfElEQVQ4T92RwQ3AIAhFWeGP4OXP4Qpu1mlcj54wIGp7bUl+NJH3ElDkn1VK0TlPTIBqrSlH2QpqrY3T7l62hH1zV1HRS7tKkgaJCfyjwSS3kiTYSQyeRwkCkml+v4N5PyTjHgAoyZEZMMgCIP8EALX4Zg9ZEnySvYa+WzdcmcbBq8asbgAAAABJRU5ErkJggg==",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAf0lEQVQ4T92QwQ3AIAhFWcERvDCHx14dpst0WRpMMB8ktteW5MdGea8K0T+r1ioxT4yDWmtLtrIM6r3P1b5RlsLYfJ0yE6VOYgI8VOggEWYeayZZBJkEbxCf4gT6p/h+nEGcj/a7OZRSxqYlAgZZtJ9i6aYFmxGyLPBO9hr6bt3KdslFSpkx4gAAAABJRU5ErkJggg==",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAe0lEQVQ4T92Q3QnAIBCDXeFG8MU5XMFVu6DlhBwxJ6WvbSAomnz+lPJP1VqnWjNJHO69Jz/CTqUxRoyYM+xY5vBV5pKPCt0gAPAmiq21ACkkARSiN9CnbAA/CRsa1ve7Pb/9g5mtRVgLKMGejzJDYA5zCdZuEodfl76rG4tbzGG8lb8mAAAAAElFTkSuQmCC",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAmklEQVQ4y2NgGJ4gXFHxPzomSVOmqur/IiMjOAbx8RqGTVOrkxOchrGRDcOqGaZ4Wqz5/79f4+EYxEc2FMUQEANZEoRBml7Wiv73FxcH0z83KmG4DMMAZEmQjSBNIINANIiP7hUUA0A2ofsfOQyQ5UAYpB4lHOTZ2cGCMIyuAaYJhkHqMWICJAjDyIqRNcEwwTSBrJhoTUMXAAA9gdSWB3sDaAAAAABJRU5ErkJggg==",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAdklEQVQ4T92Ryw3AIAxDs4JH4OI5WIH9h6Eno3wK7bWNZIFE3pMSzP5ZrbWZ88QEqPdecpTdQWOMderuZbewbzazlSwNEgn8o0CSW0kR7CSC8yhBQLLM73eQ90My7gHAJLmSAUEKgPoTAKbimz2kFPgkew19ty6f+7ZHr+Ly+QAAAABJRU5ErkJggg==",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAdElEQVQ4T92Ryw3AIAxDWcEjcPEcrMD+w8DJKB+gvbaRLJDIe1JCKf+sWuuIeWIc1FpLucp2UO99nbpb2Ra2zbai1EkksI8qkkdJEpwkguMoTkAyzW93EPdD0u8BwCC5EgFBCoD8EwCGYpstpCT4JnsNfbcmbMDUKVn45FAAAAAASUVORK5CYII=",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAh0lEQVR42mNgGJ5AUVHxPzomSZODgwMGxmsYNk2BgYFwGsZGNgyrZmTFyX2n/zeufASm0Q1FMQRmALIkSKNHyeb/8vKmYBrERzcEwwB0Q9BdgO4VFAPU1NQw/I8cBujhA1KPEg6CgoJgQRjGFgvI8iD1GDEBEoRhZMXImmCYYJpAVky0pqELAKIJyEaorix3AAAAAElFTkSuQmCC",
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAgElEQVQ4T92QwQnAIAxFs4IjeHEOV/DUIdylY3UtS4QvyVdsr23go2jeEyPyz4oxNs4T46Cc85StbAWVUsaKvZUtYdss9RhhqZNAYC8BppTaeUkPSyYBSwAC5q84gb7E/7cz4Plov5tDCKEfIgwAQrRfuPQQsc0WQiZ4J3sNfbduyMTAtdGp+U0AAAAASUVORK5CYII="
];

const TICK_LIMIT = 5000;
const MILSEC_PER_TICK = 50;
const EMPTY_SYMBOL = "[EMPTY]";
const IMMD_END_LINE = false;

// Regular Expression Patterns..
const titlepatn = /\[ti:(.+)\]/;
const artstpatn = /\[ar:(.+)\]/;
const totalpatn = /\[total:([0-9]+)\]/;
const lnpatn = /\[[0-9]+,[0-9]+\][^\[\]]+/gm;
const lninfopatn = /\[([0-9]+),([0-9]+)\]([^\[\]]+)/m;
const wordpatn = /<([0-9]+),([0-9]+),[0-9]+>([^<>]+)/;
const transpatn = /\[language:([A-Za-z0-9\/+=]+)\]/;

function getTitleCommand(tic, pos, txt) {
  return (
    (
      "execute if score @p playtick matches " +
      tic +
      " run title @a " +
      pos +
      ' "' +
      (txt == EMPTY_SYMBOL ? " " : txt) +
      '"'
    )
      .replace("\n", "")
      .replace("'", "\\'")
      .replace('"', '\\"') + "\n"
  );
}

function getTick(milsec) {
  var t = Number(milsec).toFixed(0);
  return Math.floor(t / MILSEC_PER_TICK);
}

var lyricfortick = new Array(TICK_LIMIT); // Dynamic Lyrics for each single line..
var trslnfortick = new Array(TICK_LIMIT); // Translations for each single line..
var lrclnfortick = new Array(TICK_LIMIT); // Lyrics for each single line..

function proc(src) {
  //First Process Translation Texts..
  var transb64 = src.match(transpatn);
  //for (var lang in JSON.parse(atob(transb64[1])).content)
  var transdata = JSON.parse(atob(transb64[1]));
  var translns;

  for (var idx in transdata.content) {
    if (transdata.content[idx].type == 1) {
      // Select Chinese Translation..
      translns = transdata.content[idx].lyricContent;
    }
  }
  var lns = src.match(lnpatn);
  var curtick = 0;

  for (var i in lns) {
    // Process Each Line
    var cur = lns[i].match(lninfopatn);

    if (cur) {
      var ln = cur[3];

      trslnfortick[getTick(cur[1])] = translns[i][0];

      var words = cur[3].split("<");

      var prevtext = ""; // Text of previous words in the current line..

      for (var j = 1; j < words.length; j++) {
        // Process each word, skip the first empty string..
        // Process start and end time of this word..
        var wrdstrt = getTick(Number(cur[1]) + Number(words[j].split(",")[0]));
        var wrdend;
        if (Number(j) >= words.length - 1)
          wrdend = getTick(Number(cur[2]) + Number(cur[1]));
        // Use this line's end time..
        else {
          var nxtstrt =
            Number(cur[1]) + Number(words[Number(j) + 1].split(",")[0]);
          wrdend = getTick(Number(nxtstrt)); // Use next word's start time..
        }

        var wordcont = words[j].split(">")[1]; // Word Content..
        //console.log(wrdstrt + ' {' + wordcont + '} ' + wrdend);

        var wrdln = words[j].length;
        var wordtick = wrdend - wrdstrt;
        // Allow decimal fraction here to reduce error..
        var avertick = wordtick / wrdln;

        var lettertick;

        // Display each letter in the word, one at a time..
        for (var k = 1; k < wordcont.length + 1; k++) {
          lettertick = Math.round(wrdstrt + Number(k) * avertick);
          if (lettertick < wrdstrt) lettertick = wrdstrt;

          lyricfortick[lettertick] = prevtext + wordcont.slice(0, k);
          //console.log(lettertick + ' -> ' + wordcont.slice(0,k));
        }

        if (IMMD_END_LINE && Number(j) >= words.length - 1) {
          // If it's the last word of this line, then..
          // Set the line after this one empty, if the next line follows tightly, the empty value will be overwritten..
          lyricfortick[lettertick + 1] = EMPTY_SYMBOL;
          trslnfortick[lettertick + 1] = EMPTY_SYMBOL;
          lrclnfortick[lettertick + 1] = EMPTY_SYMBOL;
        }
        prevtext += wordcont;
      }
      lrclnfortick[getTick(cur[1])] = prevtext;
    }
  }
}

// core/init.mcfunction
const INIT_MCFUNC = "IyBJbml0aWFsaXplIFJlY29yZHMuLi4KIyBUZW1wb3JhcnkgdmFyaWFibGUsIHVzZWQgdG8gaW5kaWNhdGUgd2hldGhlciBhIHBsYXllciBpcyBob2xkaW5nIGEgc3BlY2lhbCByZWNvcmQgb3Igbm90Li4uCnNjb3JlYm9hcmQgb2JqZWN0aXZlcyBhZGQgZGlzY19pZGMgbWluZWNyYWZ0LmN1c3RvbTptaW5lY3JhZnQucGxheV9yZWNvcmQKc2NvcmVib2FyZCBwbGF5ZXJzIGFkZCBAYSBkaXNjX2lkYyAwCgojIEluaXRpYWxpemUgTXVzaWMuLi4KIyBVc2VkIGJ5IGV2ZXJ5IEp1a2Vib3ggTWFya2VyLCBtYXJraW5nIHRoZSBtdXNpYyBpdCBpcyBwbGF5aW5nLi4uCnNjb3JlYm9hcmQgb2JqZWN0aXZlcyBhZGQgY3VybXVzaWMgZHVtbXkgIkN1cnJlbnQgTXVzaWMiCgojIEluaXRpYWxpemUgUGxheSBUaWNrIChGb3IgTHlyaWNzKS4uLgojIFVzZWQgYnkgZXZlcnkgSnVrZWJveCBNYXJrZXIsIG1hcmtpbmcgdGhlIHBsYXlpbmcgcHJvZ3Jlc3MuLi4Kc2NvcmVib2FyZCBvYmplY3RpdmVzIGFkZCBwbGF5dGljayBkdW1teSAiUGxheSBUaWNrIgoKIyBJbml0aWFsaXplIEx5cmljcy4uLgojIFVzZWQgYnkgZXZlcnkgZ2FtZSBwbGF5ZXIsIGluZGljYXRpbmcgdGhlIHNvbmcgdGhleSdyZSBsaXN0ZW5pbmcgdG8gYW5kIHdoaWNoIGx5cmljcyBzaG91bGQgYmUgZGlzcGxheWVkIHRvICdlbS4uLgpzY29yZWJvYXJkIG9iamVjdGl2ZXMgYWRkIGxyYyBkdW1teSAiTHlyaWNzIgpzY29yZWJvYXJkIHBsYXllcnMgYWRkIEBhIGxyYyAw";

// core/main.mcfunction
const MAIN_MCFUNC = "IyBVcGRhdGUgUGxheSBUaWNrLi4uCnNjb3JlYm9hcmQgcGxheWVycyBhZGQgQGVbdHlwZT1tYXJrZXIsdGFnPWRpc2Msc2NvcmVzPXtwbGF5dGljaz0wLi59XSBwbGF5dGljayAxCgojIFRyYWNrIEp1Y2tib3hlcy4uLgpleGVjdXRlIGFzIEBhW3Njb3Jlcz17ZGlzY19pZGM9MS4ufV0gYXQgQHMgYW5jaG9yZWQgZXllcyBydW4gZnVuY3Rpb24gcGFja25hbWVzcGFjZTpjb3JlL3RyYWNrCmV4ZWN1dGUgYXMgQGFbc2NvcmVzPXtkaXNjX2lkYz0xLi59XSBydW4gc2NvcmVib2FyZCBwbGF5ZXJzIHNldCBAcyBkaXNjX2lkYyAwCgojIENsZWFyICYgU3RvcC4uLgpleGVjdXRlIGFzIEBlW3R5cGU9bWFya2VyLHRhZz1kaXNjXSBhdCBAcyB1bmxlc3MgYmxvY2sgfiB+IH4ganVrZWJveFtoYXNfcmVjb3JkPXRydWVdIHJ1biBzdG9wc291bmQgQGFbZGlzdGFuY2U9Li4xMF0gcmVjb3JkCmV4ZWN1dGUgYXMgQGVbdHlwZT1tYXJrZXIsdGFnPWRpc2NdIGF0IEBzIHVubGVzcyBibG9jayB+IH4gfiBqdWtlYm94W2hhc19yZWNvcmQ9dHJ1ZV0gcnVuIGtpbGwgQHMKZXhlY3V0ZSBhcyBAZVt0eXBlPW1hcmtlcix0YWc9ZGlzY10gYXQgQHMgaWYgYmxvY2sgfiB+IH4ganVrZWJveFtoYXNfcmVjb3JkPXRydWVdIHJ1biBzdG9wc291bmQgQGFbZGlzdGFuY2U9Li4xMF0gcmVjb3JkIG11c2ljX2Rpc2MuZmFy";

// core/track.mcfunction
const TRACK_MCFUNC = "IyBJbml0aWFsaXplIE1hcmtlci4uLgpleGVjdXRlIGlmIGVudGl0eSBAc1tkaXN0YW5jZT0uLjRdIGlmIGRhdGEgYmxvY2sgfiB+IH4gUmVjb3JkSXRlbS50YWcuQ3VzdG9tTW9kZWxEYXRhIGFsaWduIHh5eiBydW4gc3VtbW9uIG1hcmtlciB+IH4gfiB7VGFnczpbImRpc2MiXX0KZXhlY3V0ZSBpZiBlbnRpdHkgQHNbZGlzdGFuY2U9Li40XSBpZiBkYXRhIGJsb2NrIH4gfiB+IFJlY29yZEl0ZW0udGFnLkN1c3RvbU1vZGVsRGF0YSBwb3NpdGlvbmVkIH4gfiB+IGFsaWduIHh5eiBydW4gc2NvcmVib2FyZCBwbGF5ZXJzIHNldCBAZVt0eXBlPW1hcmtlcixsaW1pdD0xXSBwbGF5dGljayAtMQpleGVjdXRlIGlmIGVudGl0eSBAc1tkaXN0YW5jZT0uLjRdIGlmIGRhdGEgYmxvY2sgfiB+IH4gUmVjb3JkSXRlbS50YWcuQ3VzdG9tTW9kZWxEYXRhIHBvc2l0aW9uZWQgfiB+IH4gYWxpZ24geHl6IHJ1biBzY29yZWJvYXJkIHBsYXllcnMgc2V0IEBlW3R5cGU9bWFya2VyLGxpbWl0PTFdIGN1cm11c2ljIDAKCiMgU3RhcnQgdG8gUGxheS4uLgpleGVjdXRlIGlmIGVudGl0eSBAc1tkaXN0YW5jZT0uLjRdIGlmIGJsb2NrIH4gfiB+IG1pbmVjcmFmdDpqdWtlYm94IHBvc2l0aW9uZWQgfiB+IH4gYWxpZ24geHl6IHJ1biBmdW5jdGlvbiBwYWNrbmFtZXNwYWNlOnBsYXkKZXhlY3V0ZSBpZiBlbnRpdHkgQHNbZGlzdGFuY2U9Li40XSB1bmxlc3MgYmxvY2sgfiB+IH4gbWluZWNyYWZ0Omp1a2Vib3ggcG9zaXRpb25lZCBeIF4gXjAuMDA1IHJ1biBmdW5jdGlvbiBwYWNrbmFtZXNwYWNlOmNvcmUvdHJhY2s=";

function getLrcCmd(index, id){
  
  return '\n';
}

function getDispLrcCmd(index, id){
  
  return '\n';
}

function clearLrc() {
  for (var i = 0; i < TICK_LIMIT; i++) {
    lyricfortick[i] = undefined;
    trslnfortick[i] = undefined;
    lrclnfortick[i] = undefined;
  }
}

function getUnderScore(str) {
  return str
    .replace(/[\.\ ]?([A-Z]+)/g, function (x, y) {
      return "_" + y.toLowerCase();
    })
    .replace(/\ /g, "_")
    .replace(/^_/, "");
}

var tempLyricsDict = new Object();

export default {
  data() {
    return {
      pagetitle: "𝕮𝖗𝖊𝖊𝖕𝖊𝖗𝖘 𝖔𝖓 𝖁𝖊𝖓𝖚𝖘",
      dragareatxt: "Drag & Drop .ogg/.txt Files",
      lyrictxt: "Lyrics",
      trslntxt: "Translation",
      records: [],
      playtick: 0,
      packnamespace: "discplus",
      discbase: "far",
      indexstart: 1000
    };
  },
  methods: {
    allowDrop(event) {
      event.preventDefault();
    },
    addRecord(id, name, artst, ln, lrc, file) {
      var ico = Math.floor(Math.random() * 12).toString();
      this.records.splice(this.records.length, 0, {
        id: id,
        icon: DEFAULT_ICONS[ico],
        name: name,
        artist: artst,
        length: ln,
        lyrics: lrc,
        file: file
      });
    },
    getFileName(fullname) {
      // Filename / Extension
      var idx = fullname.lastIndexOf(".");
      if (idx != -1) return [fullname.substr(0, idx), fullname.substr(idx + 1)];
      else return [fullname, ""];
    },
    getFile(event) {
      event.preventDefault();
      event.stopPropagation();

      //console.log(event.dataTransfer.files);
      var files = event.dataTransfer.files;
      var musicFiles = new Array();
      var lyricFiles = new Array();
      for (var i = 0; i < files.length; i++) {
        var ext = this.getFileName(files[i].name)[1];
        if (ext == "ogg") musicFiles.push(files[i]);
        else if (ext == "txt") lyricFiles.push(files[i]);
      }
      if (musicFiles.length > 0) {
        for (var fi in musicFiles) {
          var fName = musicFiles[fi].name;
          var name = this.getFileName(fName)[0];
          // Make Sure it won't change before load finishes..
          const curId = getUnderScore(getNames(name)[0]);
          for (var lrcfi in lyricFiles) {
            if (
              getUnderScore(
                getNames(this.getFileName(lyricFiles[lrcfi].name)[0])[0]
              ) == curId
            ) {
              const reader = new FileReader();
              //console.log("Lyrics Found: " + lyricFiles[lrcfi].name);
              reader.readAsText(lyricFiles[lrcfi]);
              reader.onload = function () {
                //console.log("Lyrics for " + curId + " Loaded.");
                tempLyricsDict[curId] = reader.result;
              };
              break;
            }
          }
          this.addRecord(
            curId,
            getNames(name)[0],
            getNames(name)[1],
            "?:??",
            EMPTY_SYMBOL,
            musicFiles[fi]
          );
        }
      }
      setTimeout(() => {
        this.initLyrics();
      }, event.dataTransfer.files.length * 200 + 300);
      // Initialize after all is loaded...
      return false;
    },
    initLyrics() {
      //console.log(tempLyricsDict);
      for (var rec in this.records) {
        if (tempLyricsDict[this.records[rec].id]) {
          var src = tempLyricsDict[this.records[rec].id];
          this.records[rec].name = src.match(titlepatn)[1];
          this.records[rec].artist = src.match(artstpatn)[1];
          this.records[rec].lyrics = src;
          var seconds = (Number(src.match(totalpatn)[1]) / 1000).toFixed(0);
          this.records[rec].length =
            Math.floor(seconds / 60).toString() +
            ":" +
            (seconds % 60).toString();
          //console.log(this.records[rec].id + " Lyrics set.");
        }
      }
      tempLyricsDict = new Object(); // Reset...
    },
    updateDisplay() {
      this.playtick = getTick(this.$refs["music"].currentTime * 1000);
      if (analyser) {
        var x = 0;
        analyser.getByteFrequencyData(dataArray);

        ctx.fillStyle = "#FFF";
        ctx.fillRect(0, 0, WIDTH, HEIGHT);

        for (var i = 0; i < bufferLength; i++) {
          barHeight = dataArray[i];

          var r = 220 + 48 * (i / bufferLength);
          var g = 200 + 40 * (i / bufferLength);
          var b = 255;

          ctx.fillStyle = "rgb(" + r + "," + g + "," + b + ")";
          ctx.fillRect(x, HEIGHT - barHeight, barWidth, barHeight);

          x += barWidth + 1;
        }
      }
      analyser.getByteFrequencyData(dataArray);

      if (this.playtick >= TICK_LIMIT) return;
      if (lyricfortick[this.playtick]) {
        if (lyricfortick[this.playtick] == EMPTY_SYMBOL) this.lyrictxt = "-";
        else this.lyrictxt = lyricfortick[this.playtick];
      }
      if (trslnfortick[this.playtick]) {
        if (trslnfortick[this.playtick] == EMPTY_SYMBOL) this.trslntxt = "-";
        else this.trslntxt = trslnfortick[this.playtick];
      }
    },
    initCanvas() {
      canvas = this.$refs["background"];
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      ctx = canvas.getContext("2d");
    },
    playRecord(id) {
      var name;
      var file;
      var lrc;
      for (var rec in this.records) {
        if (this.records[rec].id == id) {
          name = this.records[rec].name;
          file = this.records[rec].file;
          lrc = this.records[rec].lyrics;
          break;
        }
      }
      console.log("Now Playing: " + name);
      clearLrc();
      if (lrc != EMPTY_SYMBOL) {
        //Play Lyrics...
        proc(lrc);
      } else {
        this.lyrictxt = "-";
        this.trslntxt = "-";
      }
      if (ctx == undefined) {
        this.initCanvas();
      }
      setInterval(() => {
        this.updateDisplay();
      }, MILSEC_PER_TICK - 5);
      playing = true;
      this.$refs["music"].src = URL.createObjectURL(file);
      this.$refs["music"].play();
      context = new AudioContext();
      if (soundSrc == undefined) {
        soundSrc = context.createMediaElementSource(this.$refs["music"]);
        analyser = context.createAnalyser();
        soundSrc.connect(analyser);
        analyser.connect(context.destination);
        analyser.fftSize = FFT_SIZE;

        bufferLength = analyser.frequencyBinCount;

        dataArray = new Uint8Array(bufferLength);
      }

      WIDTH = canvas.width;
      HEIGHT = canvas.height;

      barWidth = (WIDTH / bufferLength) * 2.5;
    },
    removeRecord(id) {
      var rec;
      for (rec in this.records) {
        if (this.records[rec].id == id) {
          name = this.records[rec].name;
          break;
        }
      }
      this.records.splice(rec, 1);
    },
    testRecord() {
      var rd = Math.floor(Math.random() * 1000 + 1).toString();
      this.addRecord(
        "test_" + rd,
        "Test " + rd,
        "Someone",
        "?:??",
        EMPTY_SYMBOL,
        new File([""], "Test")
      );
    },
    selectIcon(id) {
      console.log("Select Icon: " + id);
    },
    generateResPack() {
      this.pagetitle = "Generating ResPack...";
      var zip = new JSZip();
      // FILE: ResPack/pack.mcmeta ================================================
      zip.file(
        "pack.mcmeta",
        JSON.stringify(
          {
            pack: {
              pack_format: 7,
              description: "A resource pack from Creepers on Venus!"
            }
          },
          "",
          4
        )
      );
      var mcfolder = zip.folder("assets\\minecraft");
      var soundsfolder = zip.folder("assets\\minecraft\\sounds");
      var packfolder = zip.folder("assets\\" + this.packnamespace);
      // FILE: ResPack/assets/minecraft/sounds.json ===============================
      var soundsObj = new Object();
      for (var rec in this.records) {
        soundsObj["record." + this.records[rec].id] = {
          sounds: {
            name: this.records[rec].id,
            stream: true
          }
        };
      }
      const fiblob1 = new Blob([JSON.stringify(soundsObj, "", 4)], {
        type: "application/json"
      });
      mcfolder.file("sounds.json", fiblob1, { blob: true });
      this.pagetitle = "Disc Music & Models...";
      // FILE: ResPack/assets/minecraft/sounds/<id>.ogg ===========================
      // FILE: ResPack/assets/<packnamespace>/textures/item/<id>.png ==============
      // FILE: ResPack/assets/<packnamespace>/models/item/<id>.ogg ================
      var packmodelfolder = zip.folder(
        "assets\\" + this.packnamespace + "\\models\\item"
      );
      var packtexfolder = zip.folder(
        "assets\\" + this.packnamespace + "\\textures\\item"
      );
      for (var rec in this.records) {
        var ModelObj;
        soundsfolder.file(
          this.records[rec].id + ".ogg",
          this.records[rec].file
        );
        packtexfolder.file(
          this.records[rec].id + ".png",
          dataURLtoFile(this.records[rec].icon, "rec.png")
        );
        packmodelfolder.file(
          this.records[rec].id + ".json",
          JSON.stringify(
            {
              parent: "item/generated",
              textures: {
                layer0: this.packnamespace + ":item/" + this.records[rec].id
              }
            },
            "",
            4
          )
        );
      }
      // FILE: ResPack/assets/minecraft/models/music_disc_<discbase>.json =========
      var mcmodelfolder = zip.folder("assets\\minecraft\\models\\item");
      var baseModelObj = new Object();
      baseModelObj.parent = "item/generated";
      baseModelObj.textures = {
        layer0: "item/music_disc_" + this.discbase
      };
      baseModelObj.overrides = new Array();
      for (var rec in this.records) {
        baseModelObj.overrides.push({
          predicate: {
            custom_model_data: this.indexstart + rec
          },
          model: this.packnamespace + ":item/" + this.records[rec].id
        });
      }
      const fiblob2 = new Blob([JSON.stringify(soundsObj, "", 4)], {
        type: "application/json"
      });
      mcfolder.file("sounds.json", fiblob2, { blob: true });
      this.pagetitle = "Get Your ResPack!";
      zip.generateAsync({ type: "blob" }).then(function (content) {
        saveAs(content, "DiscPlus Res Pack.zip");
      });
    },
    generateDatPack(){
      var zip = new JSZip();
      // FILE: DatPack/pack.mcmeta ================================================
      zip.file(
        "pack.mcmeta",
        JSON.stringify(
          {
            pack: {
              pack_format: 7,
              description: "A data pack from Creepers on Venus!"
            }
          },
          "",
          4
        )
      );
      // FILE: DatPack/data/minecraft/tags/functions/load.json ====================
      // FILE: DatPack/data/minecraft/tags/functions/tick.json ====================
      var mcfunctagfolder = zip.folder("data\\minecraft\\tags\\functions");
        mcfunctagfolder.file("load.json", JSON.stringify({values:[ this.packnamespace + ':core/init' ]}, "", 4));
        mcfunctagfolder.file("tick.json", JSON.stringify({values:[ this.packnamespace + ':core/main', this.packnamespace + ':display_lrc' ]}, "", 4));
      // FILE: DatPack/data/<packnamespace>/functions/core/init.mcfunction ========
      // FILE: DatPack/data/<packnamespace>/functions/core/main.mcfunction ========
      // FILE: DatPack/data/<packnamespace>/functions/core/track.mcfunction =======
      var packcorefuncfolder = zip.folder("data\\" + this.packnamespace + "\\functions\\core");
      packcorefuncfolder.file("init.mcfunction", window.atob(INIT_MCFUNC));
      packcorefuncfolder.file("main.mcfunction", window.atob(MAIN_MCFUNC).replace(/packnamespace/g, this.packnamespace));
      packcorefuncfolder.file("track.mcfunction", window.atob(TRACK_MCFUNC).replace(/packnamespace/g, this.packnamespace));
      // FILE: DatPack/data/<packnamespace>/functions/play.mcfunction =============
	  // FILE: DatPack/data/<packnamespace>/functions/display_lrc.mcfunction ======
	  var packfuncfolder = zip.folder("data\\" + this.packnamespace + "\\functions");
	  var play_cmd = '#Play Music...\n\n';
	  var disp_lrc_cmd = '#Display Lyrics...\n\n';
	  for (var rec in this.records){
	    play_cmd += getPlayCmd(rec, this.records);
		disp_lrc_cmd += getDispLrcCmd(rec, this.records);
	  }
	  packfuncfolder.file("play.mcfunction", play_cmd);
      packfuncfolder.file("display_lrc.mcfunction", lrc_cmd);
	  
	  // FILE: DatPack/data/<packnamespace>/functions/custom/<id>.mcfunction ======
	  //(FILE: DatPack/data/<packnamespace>/functions/custom/<id>_lrc.mcfunction)==
	  for (var rec in this.records){
	  	
		if (this.records[rec].lyrics && this.records[rec].lyrics != EMPTY_SYMBOL){
		  
		}
	  }
    }
  }
};
</script>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.list-enter-active,
.list-leave-active {
  transition: all 1s ease;
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(-30px);
}

#app {
  margin: auto;
  width: 88%;
  padding: 4px;
}

#respaku {
  right: 60px;
  position: fixed;
  top: 10px;
  background: cyan;
  color: white;
  cursor: default;
}
#datpaku {
  right: 180px;
  position: fixed;
  top: 10px;
  background: azure;
  cursor: default;
}
.pure-table {
  border-collapse: collapse;
  border-spacing: 0;
  empty-cells: show;
  border: 0px;
}
.pure-table td,
.pure-table th {
  border-width: 0 0 0 0;
  overflow: visible;
  padding: 0em 0em;
}

input {
  margin: auto;
  width: 95%;
  border: none;
  font-size: medium;
}

.icon {
  cursor: default;
  margin: 0;
  padding: 0;
  text-align: center;
}

/* 使tbody出现滚动条 */
table tbody {
  display: block;
  height: 200px;
  overflow-y: scroll;
}
thead,
tbody {
  display: table;
  width: 100%;
  table-layout: fixed;
}
/* 滚动条默认宽度是16px 故将tbody的宽度增加16px */
tbody {
  width: calc(100% + 16px);
}

table {
  font-family: "BIZ UDPMincho", "BIZ UDMincho", "Yu Mincho", SimSun;
}

.display {
  font-family: "FOT-Lyra Std DB", "BIZ UDPMincho", "BIZ UDMincho", "Yu Mincho",
    SimSun;
  font-size: 36px;
  height: 25px;
  width: 5000px;
  margin-left: 12px;
  margin-top: 12px;
}

#playtick {
  font-size: 18px;
}

#dragarea {
  width: 300px;
  height: 300px;
  border: 1px dashed #000;
  position: absolute;
  top: 50%;
  left: 50%;
  margin: -150px 0 0 -150px;
  text-align: center;
  font: 20px/300px "Segoe Print";
}

#dragarea2 {
  width: 75%;
  height: 100px;
  border: 1px dashed #000;
  position: absolute;
  /* top: 50%; */
  /* left: 50%; */
  margin: 5px 5px 5px 5px;
  text-align: center;
  font: 20px/90px "Segoe Print";
}

#background {
  position: absolute;
  left: 0px;
  top: 0px;
  height: 100%;
  width: 100%;
  z-index: -50;
  margin: 0;
  padding: 0;
}
#music {
  position: absolute;
  top: 100%;
  left: 10%;
  width: 80%;
  margin: auto;
  border: none;
}
#audioplate {
  position: absolute;
  top: 100%;
  left: 0;
  height: 54px;
  width: 100%;
  background: rgb(241 243 244 / 1);
  z-index: -10;
}
</style>
