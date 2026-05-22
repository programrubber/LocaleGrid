package action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class HelloWorldAction extends AnAction {
    // 사용자가 Tool에서 버튼을 클릭시 메시지 상자 하나를 표시 하는 기능
    // (필수 사항) actionPerformed 메서드로 오버라이드 해야함 (AnAction right click -> generate -> Override Methods)
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Messages.showInfoMessage("헬로 월드, Hello World????!", "Info");
    }
}
