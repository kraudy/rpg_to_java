**FREE

// Prototype for the system() API
Dcl-Pr system Int(10) ExtProc('system');
  cmdString Pointer Value Options(*String);
End-Pr;

// Prototype for QMHSNDPM (Send Program Message)
Dcl-Pr QMHSNDPM ExtPgm('QMHSNDPM');
  msgID Char(7) Const;
  msgFile Char(20) Const;
  msgData Char(256) Const;
  msgDataLen Int(10) Const;
  msgType Char(10) Const;
  callStackEntry Char(10) Const;
  callStackCounter Int(10) Const;
  msgKey Char(4);
  errorCode Char(32767) Options(*VarSize);
End-Pr;

// Prototype for QMHRCVPM (Receive Program Message)
Dcl-Pr QMHRCVPM ExtPgm('QMHRCVPM');
  msgInfo Char(32767) Options(*VarSize);
  msgInfoLen Int(10) Const;
  formatName Char(8) Const;
  callStackEntry Char(10) Const;
  callStackCounter Int(10) Const;
  msgType Char(10) Const;
  msgKey Char(4) Const;
  waitTime Int(10) Const;
  msgAction Char(10) Const;
  errorCode Char(32767) Options(*VarSize);
End-Pr;

// Message receiver structure for RCVM0100
Dcl-DS msgInfo Qualified;
  bytesReturned Int(10);
  bytesAvailable Int(10);
  msgLen Int(10);
  replyLen Int(10);
  reply Char(10) Pos(141);
End-DS;

// Variables
Dcl-S cmd Char(256);
Dcl-S rc Int(10);
Dcl-S dirPath Char(100) Inz('/somedir/subdir');
Dcl-S msgData Char(256);
Dcl-S msgKey Char(4);
Dcl-S errorCode Char(8) Inz(*Blanks);
Dcl-S reply Char(1);
Dcl-S retry Ind Inz(*Off);

// Main logic
// Validate directory path
If dirPath = *Blanks;
  Dsply 'Error: Directory path is blank';
  *InLr = *On;
  Return;
EndIf;

// Loop for retry logic
DoU retry = *Off;
  // Build the RMDIR command
  cmd = 'RMDIR DIR(''' + %Trim(dirPath) + ''') SUBTREE(*ALL)';

  // Execute the command
  rc = system(cmd);

  // Check return code
  If rc = 0;
    // Success: Directory was deleted (or didn't exist)
    Dsply 'Directory deleted successfully';
    retry = *Off;
  Else;
    // Failure: Send inquiry message to operator
    msgData = 'Error deleting directory ' + %Trim(dirPath) +
              '. Reply S to skip, R to retry.';
    QMHSNDPM('CPF9898': 'QCPFMSG   *LIBL': msgData: %Len(%Trim(msgData)):
             '*INQ': '*SYSOPR': 0: msgKey: errorCode);

    // Receive the operator's reply
    Clear msgInfo;
    QMHRCVPM(msgInfo: %Size(msgInfo): 'RCVM0100': '*SYSOPR': 0:
             '*REPLY': msgKey: 60: '*REMOVE': errorCode);

    // Extract the reply (first character)
    If msgInfo.replyLen > 0;
      reply = %Subst(msgInfo.reply: 1: 1);
    Else;
      reply = 'S'; // Default to skip if no reply
      Dsply 'No reply received, defaulting to skip';
    EndIf;

    // Process the reply
    If reply = 'S';
      Dsply 'Operator chose to skip';
      retry = *Off;
    ElseIf reply = 'R';
      Dsply 'Operator chose to retry';
      retry = *On;
    Else;
      Dsply 'Invalid reply received, defaulting to skip';
      retry = *Off;
    EndIf;
  EndIf;
EndDo;

// End program
*InLr = *On;
Return;