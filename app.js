const video=document.getElementById("gameFeed");
const startButton=document.getElementById("startCapture");
const stopButton=document.getElementById("stopCapture");
const status=document.getElementById("status");
const feedState=document.getElementById("feedState");
const placeholder=document.getElementById("videoPlaceholder");
const command=document.getElementById("command");
const reason=document.getElementById("reason");
const nextCommand=document.getElementById("nextCommand");
const log=document.getElementById("log");
let stream=null;

const tacticalCommands=[
 {command:"HOLD",reason:"No confirmed advantage. Gather information before moving."},
 {command:"FALL BACK",reason:"Your current position is becoming dangerous. Preserve your life."},
 {command:"ROTATE",reason:"Your current position is losing strategic value. Move before the enemy does."},
 {command:"PUSH RIGHT",reason:"Enemy attention is elsewhere. Use the opening before it disappears."},
 {command:"HOLD FIRE",reason:"Do not reveal your position. Wait for a better engagement."},
 {command:"REPOSITION",reason:"You have been seen. Change position before taking another fight."}
];

async function startCapture(){
  try{
    stream=await navigator.mediaDevices.getDisplayMedia({video:{frameRate:30},audio:false});
    video.srcObject=stream;
    status.textContent="LIVE";
    status.classList.remove("offline");
    status.classList.add("online");
    feedState.textContent="LIVE FEED";
    placeholder.classList.add("hidden");
    startButton.disabled=true;
    stopButton.disabled=false;
    addLog("COMMANDER","Live gameplay feed connected.");
    stream.getVideoTracks()[0].addEventListener("ended",stopCapture);
  }catch(error){
    console.error(error);
    addLog("SYSTEM","Screen capture was cancelled or unavailable.");
  }
}

function stopCapture(){
  if(stream){stream.getTracks().forEach(track=>track.stop());stream=null;}
  video.srcObject=null;
  status.textContent="OFFLINE";
  status.classList.remove("online");
  status.classList.add("offline");
  feedState.textContent="NO FEED";
  placeholder.classList.remove("hidden");
  startButton.disabled=false;
  stopButton.disabled=true;
  addLog("COMMANDER","Gameplay feed disconnected.");
}

function simulateCommand(){
  const random=tacticalCommands[Math.floor(Math.random()*tacticalCommands.length)];
  command.textContent=random.command;
  reason.textContent=random.reason;
  addLog("COMMAND",`${random.command} — ${random.reason}`);
}

function addLog(source,message){
  const entry=document.createElement("div");
  entry.className="log-entry";
  entry.innerHTML=`<span>${source}</span>${message}`;
  log.prepend(entry);
}

startButton.addEventListener("click",startCapture);
stopButton.addEventListener("click",stopCapture);
nextCommand.addEventListener("click",simulateCommand);
