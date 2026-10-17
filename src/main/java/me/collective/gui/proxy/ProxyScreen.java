package me.collective.gui.proxy;

import java.util.List;
import me.collective.proxy.ProxyEntry;
import me.collective.proxy.ProxyManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

@Environment(EnvType.CLIENT)
public class ProxyScreen extends Screen {
   private final Screen parent;
   private TextFieldWidget hostField;
   private TextFieldWidget portField;
   private TextFieldWidget userField;
   private TextFieldWidget passField;
   private ProxyEntry.ProxyType type = ProxyEntry.ProxyType.SOCKS5;
   private int hoveredProxy = -1;
   private int hoveredDelete = -1;
   private boolean hoverType = false;
   private boolean hoverAdd = false;
   private boolean hoverBack = false;
   private boolean hoverDirect = false;
   private boolean hoverToggle = false;
   private static final int W = 300;
   private static final int H = 340;
   private static final int COL_BG = -15987700;
   private static final int COL_CARD = -15461356;
   private static final int COL_CARD_HOVER = -14935012;
   private static final int COL_BORDER = -14013910;
   private static final int COL_BORDER_ACT = -11184811;
   private static final int COL_BORDER_HL = -7829368;
   private static final int COL_PANEL = -15658735;
   private static final int COL_BTN = -15198184;
   private static final int COL_BTN_HOVER = -14540254;
   private static final int COL_DEL = -15070710;
   private static final int COL_DEL_HOVER = -14020592;
   private static final int COL_DEL_BRD = -12970731;
   private static final int COL_ACT = -15922688;
   private static final int COL_ACT_HOVER = -14806016;
   private static final int COL_ACT_BRD = -12965888;

   public ProxyScreen(Screen parent) {
      super(Text.literal("Proxy"));
      this.parent = parent;
   }

   private TextRenderer tr() {
      return MinecraftClient.getInstance().textRenderer;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
      // Фон рисуем сами в render(): тёмная заливка + панель.
      // Виджеты (поля ввода) дорисовывает super.render() поверх.
   }

   protected void init() {
      int x = (this.width - 300) / 2;
      int y = (this.height - 340) / 2;
      this.hostField = new TextFieldWidget(this.textRenderer, x + 8, y + 52, 184, 16, Text.literal("IP"));
      this.hostField.setMaxLength(64);
      this.hostField.setPlaceholder(Text.literal("IP / хост"));
      this.addDrawableChild(this.hostField);

      this.portField = new TextFieldWidget(this.textRenderer, x + 200, y + 52, 92, 16, Text.literal("Port"));
      this.portField.setMaxLength(5);
      this.portField.setTextPredicate(s -> s.matches("\\d*"));
      this.portField.setPlaceholder(Text.literal("Порт"));
      this.addDrawableChild(this.portField);

      this.userField = new TextFieldWidget(this.textRenderer, x + 8, y + 76, 138, 16, Text.literal("Login"));
      this.userField.setMaxLength(64);
      this.userField.setPlaceholder(Text.literal("Логин (необязательно)"));
      this.addDrawableChild(this.userField);

      this.passField = new TextFieldWidget(this.textRenderer, x + 154, y + 76, 138, 16, Text.literal("Pass"));
      this.passField.setMaxLength(64);
      this.passField.setPlaceholder(Text.literal("Пароль (необязательно)"));
      this.addDrawableChild(this.passField);

      this.setInitialFocus(this.hostField);
   }

   private void doAdd() {
      String host = this.hostField.getText().trim();
      int port;
      try {
         port = Integer.parseInt(this.portField.getText().trim());
      } catch (Exception e) {
         return;
      }
      if (host.isEmpty() || port <= 0 || port > 65535) return;
      ProxyManager.INSTANCE.getProxies().add(new ProxyEntry(
         this.type, host, port, this.userField.getText().trim(), this.passField.getText()));
      ProxyManager.INSTANCE.save();
      this.hostField.setText("");
      this.portField.setText("");
      this.userField.setText("");
      this.passField.setText("");
      this.setInitialFocus(this.hostField);
   }

   public void render(DrawContext ctx, int mx, int my, float delta) {
      int w = this.width;
      int h = this.height;
      int x = (w - 300) / 2;
      int y = (h - 340) / 2;
      ctx.fill(0, 0, w, h, -1442840576);
      ctx.fill(x, y, x + 300, y + 340, -15987700);
      this.rect(ctx, x, y, 300, 340, -14013910);
      ctx.drawText(this.tr(), "Proxy", x + 10, y + 10, -1, false);
      ctx.fill(x, y + 22, x + 300, y + 23, -14013910);
      ProxyEntry active = ProxyManager.INSTANCE.getActive();
      String curLabel = "Активный: " + (active == null ? "прямое" : active.displayName());
      if (active != null && !ProxyManager.INSTANCE.isEnabled()) curLabel += " (выкл)";
      if (this.tr().getWidth(curLabel) > 280) {
         curLabel = this.tr().trimToWidth(curLabel, 280, true);
      }
      ctx.drawText(this.tr(), curLabel, x + 10, y + 29, -11184811, false);
      ctx.fill(x, y + 40, x + 300, y + 41, -14013910);

      int btnY = y + 100;
      int typeBtnX = x + 8;
      int typeBtnW = 138;
      this.hoverType = mx >= typeBtnX && mx <= typeBtnX + typeBtnW && my >= btnY && my <= btnY + 16;
      ctx.fill(typeBtnX, btnY, typeBtnX + typeBtnW, btnY + 16, this.hoverType ? -14540254 : -15198184);
      this.rect(ctx, typeBtnX, btnY, typeBtnW, 16, this.hoverType ? -11184811 : -14013910);
      String typeLabel = this.type == ProxyEntry.ProxyType.SOCKS5 ? "SOCKS5" : "HTTP";
      ctx.drawText(this.tr(), typeLabel, typeBtnX + (typeBtnW - this.tr().getWidth(typeLabel)) / 2, btnY + 4, this.hoverType ? -1 : -8947849, false);

      int addBtnX = x + 154;
      int addBtnW = 138;
      this.hoverAdd = mx >= addBtnX && mx <= addBtnX + addBtnW && my >= btnY && my <= btnY + 16;
      ctx.fill(addBtnX, btnY, addBtnX + addBtnW, btnY + 16, this.hoverAdd ? -14540254 : -15198184);
      this.rect(ctx, addBtnX, btnY, addBtnW, 16, this.hoverAdd ? -11184811 : -14013910);
      String addLabel = "Добавить";
      ctx.drawText(this.tr(), addLabel, addBtnX + (addBtnW - this.tr().getWidth(addLabel)) / 2, btnY + 4, this.hoverAdd ? -1 : -8947849, false);

      int listStartY = btnY + 26;
      ctx.fill(x, listStartY - 4, x + 300, listStartY - 3, -14013910);

      List<ProxyEntry> list = ProxyManager.INSTANCE.getProxies();
      if (list.isEmpty()) {
         ctx.drawText(
            this.tr(), "Нет сохранённых прокси", x + (300 - this.tr().getWidth("Нет сохранённых прокси")) / 2, listStartY + 6, -13421773, false
         );
      }

      this.hoveredProxy = -1;
      this.hoveredDelete = -1;

      for (int i = 0; i < list.size(); i++) {
         ProxyEntry entry = list.get(i);
         int ay = listStartY + i * 24;
         if (ay + 20 > y + 340 - 46) {
            break;
         }

         int cardW = 284;
         int delW = 18;
         int rowW = cardW - delW - 4;
         boolean hovRow = mx >= x + 8 && mx <= x + 8 + rowW && my >= ay && my <= ay + 18;
         boolean hovDel = mx >= x + 8 + rowW + 4 && mx <= x + 8 + cardW && my >= ay && my <= ay + 18;
         if (hovRow) {
            this.hoveredProxy = i;
         }

         if (hovDel) {
            this.hoveredDelete = i;
         }

         ctx.fill(x + 8, ay, x + 8 + rowW, ay + 18, hovRow ? -14935012 : -15461356);
         this.rect(ctx, x + 8, ay, rowW, 18, hovRow ? -11184811 : -14013910);
         boolean isActive = active != null && entry.equals(active);
         ctx.fill(x + 13, ay + 7, x + 16, ay + 10, isActive ? -7798904 : -12303292);
         String rowLabel = entry.displayName();
         int maxRowW = rowW - 20;
         if (this.tr().getWidth(rowLabel) > maxRowW) {
            rowLabel = this.tr().trimToWidth(rowLabel, maxRowW) + "...";
         }

         ctx.drawText(this.tr(), rowLabel, x + 19, ay + 5, isActive ? -3342388 : (hovRow ? -1 : -6710887), false);
         ctx.fill(x + 8 + rowW + 4, ay, x + 8 + cardW, ay + 18, hovDel ? -14020592 : -15070710);
         this.rect(ctx, x + 8 + rowW + 4, ay, delW, 18, hovDel ? -8969694 : -12970731);
         String xMark = "✕";
         ctx.drawText(this.tr(), xMark, x + 8 + rowW + 4 + (delW - this.tr().getWidth(xMark)) / 2, ay + 5, hovDel ? -43691 : -11197918, false);
      }

      int bottomPanelY = y + 340 - 40;
      ctx.fill(x, bottomPanelY, x + 300, bottomPanelY + 1, -14013910);
      int backY = y + 340 - 22;
      int toggleBtnX = x + 8;
      int toggleBtnW = 92;
      boolean on = ProxyManager.INSTANCE.isEnabled();
      this.hoverToggle = mx >= toggleBtnX && mx <= toggleBtnX + toggleBtnW && my >= backY && my <= backY + 14;
      ctx.fill(toggleBtnX, backY, toggleBtnX + toggleBtnW, backY + 14, this.hoverToggle ? -14540254 : -15198184);
      this.rect(ctx, toggleBtnX, backY, toggleBtnW, 14, this.hoverToggle ? -7842560 : -12965888);
      String toggleLabel = on ? "ВКЛ" : "ВЫКЛ";
      int toggleColor = on ? -3342388 : -7842526;
      ctx.drawText(
         this.tr(), toggleLabel, toggleBtnX + (toggleBtnW - this.tr().getWidth(toggleLabel)) / 2, backY + 3, toggleColor, false
      );

      int directBtnX = x + 108;
      int directBtnW = 92;
      this.hoverDirect = mx >= directBtnX && mx <= directBtnX + directBtnW && my >= backY && my <= backY + 14;
      ctx.fill(directBtnX, backY, directBtnX + directBtnW, backY + 14, this.hoverDirect ? -14806016 : -15922688);
      this.rect(ctx, directBtnX, backY, directBtnW, 14, this.hoverDirect ? -7842560 : -12965888);
      String directLabel = "Прямое";
      ctx.drawText(
         this.tr(), directLabel, directBtnX + (directBtnW - this.tr().getWidth(directLabel)) / 2, backY + 3, this.hoverDirect ? -21965 : -7842526, false
      );

      int backBtnX = x + 208;
      int backBtnW = 84;
      this.hoverBack = mx >= backBtnX && mx <= backBtnX + backBtnW && my >= backY && my <= backY + 14;
      ctx.fill(backBtnX, backY, backBtnX + backBtnW, backY + 14, this.hoverBack ? -14540254 : -15198184);
      this.rect(ctx, backBtnX, backY, backBtnW, 14, this.hoverBack ? -11184811 : -14013910);
      String backLabel = "← Назад";
      ctx.drawText(this.tr(), backLabel, backBtnX + (backBtnW - this.tr().getWidth(backLabel)) / 2, backY + 3, this.hoverBack ? -1 : -10066330, false);
      super.render(ctx, mx, my, delta);
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      int w = this.width;
      int h = this.height;
      int x = (w - 300) / 2;
      int y = (h - 340) / 2;
      int btnY = y + 100;
      if (click.button() == 0) {
         if (mx >= x + 8 && mx <= x + 146 && my >= btnY && my <= btnY + 16) {
            this.type = this.type == ProxyEntry.ProxyType.SOCKS5
               ? ProxyEntry.ProxyType.HTTP : ProxyEntry.ProxyType.SOCKS5;
            return true;
         }
         if (mx >= x + 154 && mx <= x + 292 && my >= btnY && my <= btnY + 16) {
            this.doAdd();
            return true;
         }
      }
      int listStartY = btnY + 26;
      List<ProxyEntry> list = ProxyManager.INSTANCE.getProxies();

      for (int i = 0; i < list.size(); i++) {
         int ay = listStartY + i * 24;
         if (ay + 20 > y + 340 - 46) {
            break;
         }

         int cardX = x + 8;
         int cardW = 284;
         int delW = 18;
         int rowW = cardW - delW - 4;
         int delX = cardX + rowW + 4;
         if (mx >= delX && mx <= delX + delW && my >= ay && my <= ay + 18) {
            ProxyManager.INSTANCE.remove(i);
            return true;
         }

         if (mx >= cardX && mx <= cardX + rowW && my >= ay && my <= ay + 18) {
            ProxyManager.INSTANCE.setActive(i);
            return true;
         }
      }

      int backY = y + 340 - 22;
      int toggleBtnX = x + 8;
      int toggleBtnW = 92;
      int directBtnX = x + 108;
      int directBtnW = 92;
      int backBtnX = x + 208;
      int backBtnW = 84;
      if (mx >= toggleBtnX && mx <= toggleBtnX + toggleBtnW && my >= backY && my <= backY + 14) {
         ProxyManager.INSTANCE.toggle();
         return true;
      } else if (mx >= directBtnX && mx <= directBtnX + directBtnW && my >= backY && my <= backY + 14) {
         ProxyManager.INSTANCE.setDirect();
         return true;
      } else if (mx >= backBtnX && mx <= backBtnX + backBtnW && my >= backY && my <= backY + 14) {
         MinecraftClient.getInstance().setScreen(this.parent);
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   public boolean keyPressed(KeyInput input) {
      if (input.key() == 256) {
         MinecraftClient.getInstance().setScreen(this.parent);
         return true;
      }
      if (input.key() == 257) {
         this.doAdd();
         return true;
      }
      return super.keyPressed(input);
   }

   public boolean shouldPause() {
      return false;
   }

   private void rect(DrawContext ctx, int x, int y, int w, int h, int c) {
      ctx.fill(x, y, x + w, y + 1, c);
      ctx.fill(x, y + h - 1, x + w, y + h, c);
      ctx.fill(x, y, x + 1, y + h, c);
      ctx.fill(x + w - 1, y, x + w, y + h, c);
   }
}
