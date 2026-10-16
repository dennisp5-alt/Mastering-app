from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"app/src/main/java/com/dennis/autofixcolourlab/MainActivity.java"
s=p.read_text()
s=s.replace("private static final int AMBER =", "private static final int INK = Color.rgb(236, 242, 249);\n    private static final int GOLD_INK = Color.rgb(24, 27, 34);\n    private static final int AMBER =")
for size in ("14","13","12"):
    s=s.replace(size+", NAVY, true",size+", INK, true")
s=s.replace("primary ? WHITE : NAVY","primary ? GOLD_INK : INK")
s=s.replace("button.setTextColor(selected ? WHITE : (enabled ? NAVY : MUTED));","button.setTextColor(selected ? GOLD_INK : (enabled ? INK : MUTED));")
s=s.replace("centeredText(names[i],11,Color.WHITE,true)","centeredText(names[i],11,i==0?GOLD_INK:INK,true)")
s=s.replace("for(int j=0;j<4;j++)buttons[j].setBackground(rounded(j==index?BLUE:WHITE,10,1,BORDER));","for(int j=0;j<4;j++){\n                    buttons[j].setBackground(rounded(j==index?BLUE:WHITE,10,1,BORDER));\n                    buttons[j].setTextColor(j==index?GOLD_INK:INK);\n                }")
s=s.replace("v0.7.3 STABLE TEST","v0.7.4 STABLE TEST")
p.write_text(s)
for rel in ("app/build.gradle","build-apk.sh","run-tests.sh"):
    q=root/rel
    t=q.read_text().replace("0.7.3","0.7.4").replace("versionCode 11","versionCode 12").replace("--version-code 11","--version-code 12")
    q.write_text(t)
print("Applied v0.7.4 contrast fixes without modifying processing engines")
