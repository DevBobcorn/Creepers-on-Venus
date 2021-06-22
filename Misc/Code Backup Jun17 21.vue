<!-- Use preprocessors via the lang attribute! e.g. <template lang="pug"> -->
<template>
  <div id="app">
    <canvas id="background" ref="background"></canvas>
    <audio ref="music" controls="controls"></audio>
    <div id="audioplate"></div>
    <h1 v-once>{{ pagetitle }}</h1>
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
    <span style="position: relative; top: 110px" v-if="records.length > 0">
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

function getNames(str) {
  // Music Name / Artist Name
  var idx = str.lastIndexOf("-");
  if (idx >= 0)
    return [str.substring(idx + 1).trim(), str.substring(0, idx).trim()];
  else return [str, "Unknown"];
}

const DEFAULT_ICONS = [
  "https://i.loli.net/2021/06/17/Ox7cKRsEtwgz8oW.png",
  "https://i.loli.net/2021/06/17/3XueA5WkUtzhQNT.png",
  "https://i.loli.net/2021/06/17/8VIumf6yMvZBDi2.png",
  "https://i.loli.net/2021/06/17/bqJlYAIf8niGWKo.png",
  "https://i.loli.net/2021/06/17/MI7d2F3QDOJf9nl.png",
  "https://i.loli.net/2021/06/17/sn12R5zYIXk8bSv.png",
  "https://i.loli.net/2021/06/17/2BYTOpnSzFiI8jw.png",
  "https://i.loli.net/2021/06/17/w6WY2Ve4vqfhsxK.png",
  "https://i.loli.net/2021/06/17/RZoUD5MmpkurKBP.png",
  "https://i.loli.net/2021/06/17/2VbfSlXRHgjKDxQ.png",
  "https://i.loli.net/2021/06/17/9NMkmBHTrQVLYpy.png",
  "https://i.loli.net/2021/06/17/VGr6HRU3oeixdIL.png",
  "https://i.loli.net/2021/06/17/Prf9qlLzmHRn6go.png"
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
      var zip = new JSZip();
      // FILE: ResPack/pack.mcmeta
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
      // FILE: ResPack/assets/minecraft/sounds.json
      var soundsObj = new Object();
      for (var rec in this.records) {
        soundsObj["record." + this.records[rec].id] = {
          sounds: {
            name: this.records[rec].id,
            stream: true
          }
        };
      }
      const fiblob = new Blob([JSON.stringify(soundsObj, "", 4)], {
        type: "application/json"
      });
      mcfolder.file("sounds.json", fiblob, { blob: true });
      // FILE: ResPack/assets/minecraft/sounds/<id>.ogg
      for (var rec in this.records) {
        soundsfolder.file(
          this.records[rec].id + ".ogg",
          this.records[rec].file
        );
      }

      zip.generateAsync({ type: "blob" }).then(function (content) {
        saveAs(content, "DiscPlus Res Pack.zip");
      });
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
  display: inline;
  cursor: default;
  margin: 0;
  padding: 0;
}

/* tbody出现滚动条 */
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
/* 滚动条默认宽度是16px 将tbody的宽度加16px */
tbody {
  width: calc(100% + 16px);
}

table {
  font-family: "BIZ UDPMincho", "BIZ UDMincho", "Yu Mincho", SimSun;
}

.display {
  font-family: "BIZ UDPMincho", "BIZ UDMincho", "Yu Mincho", SimSun;
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

#audioplate {
  position: absolute;
  top: 100%;
  left: 0;
  height: 54px;
  width: 100%;
  background: rgb(241 243 244 / 1);
  z-index: -10;
}
audio {
  position: absolute;
  top: 100%;
  left: 10%;
  width: 80%;
  margin: auto;
  border: none;
}
</style>
