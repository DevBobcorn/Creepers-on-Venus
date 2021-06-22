<!-- Use preprocessors via the lang attribute! e.g. <template lang="pug"> -->
<template>
  <div id="app">
    <h1 v-once>{{ pagetitle }}</h1>
    <table class="pure-table pure-table-horizontal">
      <tr>
        <th width="25%">Id</th>
        <th width="26%">Name</th>
        <th width="23%">Artist</th>
        <th width="10%">Length</th>
        <th width="10%">Lyrics</th>
        <th width="3%">》</th>
        <th width="3%">X</th>
      </tr>
      <tr v-for="record in records">
        <td><input v-model="record.id" /></td>
        <td><input v-model="record.name" /></td>
        <td><input v-model="record.artist" /></td>
        <td>{{ record.length }}</td>
        <td><input v-model="record.lyrics" /></td>
        <td>
          <button @click="playRecord(record.id)" v-bind:title="record.id">
            &gt;
          </button>
        </td>
        <td>
          <button>X</button>
        </td>
      </tr>
    </table>
    <input
      type="file"
      id="fileOgg"
      ref="fileOgg"
      multiple="multiple"
      @change="getFile($event)"
      value=""
      name="upload"
      accept=".ogg,.txt"
    />
    <p id="lyrictxt">Play Tick: {{ playtick }}</p>
    <p class="display" id="lyrictxt">{{ lyrictxt }}</p>
    <p class="display" id="trslntxt">{{ trslntxt }}</p>
    <audio ref="music" controls="controls"></audio>
  </div>
</template>

<script>
var playing = false;

function getNames(str) {
  // Music Name / Artist Name
  var idx = str.lastIndexOf("-");
  if (idx >= 0)
    return [str.substring(idx + 1).trim(), str.substring(0, idx).trim()];
  else return [str, "Unknown"];
}

const TICK_LIMIT = 5000;
const MILSEC_PER_TICK = 50;
const EMPTY_SYMBOL = "[EMPTY]";
const IMMD_END_LINE = false;

// Regular Expression Patterns..
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
    .replace(" ", "_")
    .replace(/^_/, "");
}

var tempLyricsDict = new Object();

export default {
  data() {
    return {
      pagetitle: "Creepers on Venus",
      lyrictxt: "Lyrics",
      trslntxt: "Translation",
      records: [],
      playtick: 0
    };
  },
  methods: {
    addRecord(id, name, artst, ln, lrc, file) {
      this.records.splice(this.records.length, 0, {
        id: id,
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
      var files = event.target.files;
      var musicFiles = new Array();
      var lyricFiles = new Array();
      for (var i = 0; i < files.length; i++) {
        var ext = this.getFileName(files[i].name)[1];
        console.log(ext);
        if (ext == "ogg") musicFiles.push(files[i]);
        else lyricFiles.push(files[i]);
      }
      if (musicFiles.length > 0) {
        for (var fi in musicFiles) {
          var fName = musicFiles[fi].name;
          var name = this.getFileName(fName)[0];
          // Make Sure it won't change before load finishes..
          const curId = getUnderScore(getNames(name)[0]);

          for (var lrcfi in lyricFiles) {
            console.log(
              getUnderScore(
                getNames(this.getFileName(lyricFiles[lrcfi].name)[0])[0]
              ),
              curId
            );
            if (
              getUnderScore(
                getNames(this.getFileName(lyricFiles[lrcfi].name)[0])[0]
              ) == curId
            ) {
              const reader = new FileReader();
              console.log("Lyrics Found: " + lyricFiles[lrcfi].name);
              reader.readAsText(lyricFiles[lrcfi]);
              reader.onload = function () {
                console.log("Lyrics for " + curId + " Loaded.");
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
      }, 800);
    },
    initLyrics() {
      console.log(tempLyricsDict);
      for (var rec in this.records) {
        if (tempLyricsDict[this.records[rec].id]) {
          this.records[rec].lyrics = tempLyricsDict[this.records[rec].id];
          console.log(this.records[rec].id + " Lyrics set.");
        }
      }
    },
    displayLyrics() {
      this.playtick = getTick(this.$refs["music"].currentTime * 1000);
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
        if (!playing) {
          setInterval(() => {
            this.displayLyrics();
          }, 50);
          playing = true;
        }
      } else {
        this.lyrictxt = "-";
        this.trslntxt = "-";
      }

      this.$refs["music"].src = URL.createObjectURL(file);
      this.$refs["music"].play();
    }
  }
};
</script>

<style>
#app {
  margin: auto;
  width: 94%;
  padding: 4px;
}

.pure-table {
  border-collapse: collapse;
  border-spacing: 0;
  empty-cells: show;
  border: 1px solid #cbcbcb;
}

.pure-table td,
.pure-table th {
  border-left: 1px solid #cbcbcb;
  border-width: 0 0 0 1px;
  font-size: inherit;
  margin: 0;
  overflow: visible;
  padding: 0em 0em;
}
.pure-table-horizontal td,
.pure-table-horizontal th {
  border-width: 0 0 1px 0;
  border-bottom: 1px solid #cbcbcb;
}

.pure-table-horizontal tbody > tr:last-child > td {
  border-bottom-width: 0;
}

p {
  font-family: "BIZ UDPMincho", SimSun;
}

input {
  margin: auto;
  width: 95%;
  border: none;
}

audio {
  margin: auto;
  width: 95%;
  border: none;
}

.display {
  font-family: SimSun, "BIZ UDPMincho";
  font-size: 36px;
  height: 25px;
  width: 5000px;
  margin-left: 12px;
  margin-top: 12px;
}
</style>
