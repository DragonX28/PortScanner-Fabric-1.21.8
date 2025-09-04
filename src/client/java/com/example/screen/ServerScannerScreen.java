package com.example.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.example.mixin.client.ServerListAccessor;

public class ServerScannerScreen extends Screen {
    private final ServerList serverList;
    private TextFieldWidget ipField, startPortField, endPortField;
    private ButtonWidget scanButton;
    private ButtonWidget addServersButton;

    private volatile boolean scanning = false;
    private volatile int openServersCount = 0;
    private volatile int checkedPortsCount = 0;

    private final List<String> foundServers = new ArrayList<>();

    private long lastRenderLogTime = 0;

    public ServerScannerScreen(ServerList serverList) {
    	super(Text.translatable("screen.serverscanner.title"));
        this.serverList = serverList;
    }

    @Override
    protected void init() {
        int widthCenter = this.width / 2;

        if (ipField == null) {
        	ipField = new TextFieldWidget(this.textRenderer, widthCenter - 150, 20, 300, 20, Text.translatable("screen.serverscanner.static_ip"));
            ipField.setText("127.0.0.1");
        } else {
            ipField.setX(widthCenter - 150);
            ipField.setY(20);
            ipField.setWidth(300);
        }

        if (startPortField == null) {
        	startPortField = new TextFieldWidget(this.textRenderer, widthCenter - 150, 50, 140, 20, Text.translatable("screen.serverscanner.start_port"));
            startPortField.setText("25565");
        } else {
            startPortField.setX(widthCenter - 150);
            startPortField.setY(50);
            startPortField.setWidth(140);
        }

        if (endPortField == null) {
        	endPortField = new TextFieldWidget(this.textRenderer, widthCenter + 10, 50, 140, 20, Text.translatable("screen.serverscanner.end_port"));
            endPortField.setText("25575");
        } else {
            endPortField.setX(widthCenter + 10);
            endPortField.setY(50);
            endPortField.setWidth(140);
        }

        this.clearChildren();
        this.addDrawableChild(ipField);
        this.addDrawableChild(startPortField);
        this.addDrawableChild(endPortField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.serverscanner.back"), button -> {
            MinecraftClient.getInstance().setScreen(new MultiplayerScreen(this));
        }).position(widthCenter - 150, 80).size(60, 20).build());

        if (scanButton == null) {
            scanButton = ButtonWidget.builder(Text.translatable(scanning ? "screen.serverscanner.stop_scan" : "screen.serverscanner.start_scan"), button -> toggleScan())
                    .position(widthCenter - 70, 80).size(140, 20).build();
        } else {
            scanButton.setMessage(Text.translatable(scanning ? "screen.serverscanner.stop_scan" : "screen.serverscanner.start_scan"));
            scanButton.setPosition(widthCenter - 70, 80);
        }
        this.addDrawableChild(scanButton);

        if (addServersButton == null) {
            addServersButton = ButtonWidget.builder(Text.translatable("screen.serverscanner.add_all"), button -> addAllServers())
                    .position(widthCenter + 80, 80).size(100, 20).build();
        } else {
            addServersButton.setPosition(widthCenter + 80, 80);
        }
        this.addDrawableChild(addServersButton);
    }

    private void toggleScan() {
        if (scanning) {
            scanning = false;
            scanButton.setMessage(Text.translatable("screen.serverscanner.start_scan"));
        } else {
            scanning = true;
            scanButton.setMessage(Text.translatable("screen.serverscanner.stop_scan"));
            openServersCount = 0;
            checkedPortsCount = 0;
            foundServers.clear();
            startScan();
        }
    }

    private void startScan() {
        final String ip = ipField.getText();
        final int startPort;
        final int endPort;

        try {
            startPort = Integer.parseInt(startPortField.getText());
            endPort = Integer.parseInt(endPortField.getText());
        } catch (NumberFormatException e) {
            scanning = false;
            return;
        }

        if (startPort < 1 || endPort > 65535 || startPort > endPort) {
            scanning = false;
            return;
        }

        CompletableFuture.runAsync(() -> {
            for (int port = startPort; port <= endPort && scanning; port++) {
                checkedPortsCount++;
                if (isMinecraftServerOnline(ip, port)) {
                    openServersCount++;
                    String serverAddress = ip + ":" + port;
                    synchronized (foundServers) {
                        if (!foundServers.contains(serverAddress)) {
                            foundServers.add(serverAddress);
                        }
                    }
                }
            }
            scanning = false;
            MinecraftClient.getInstance().execute(() -> scanButton.setMessage(Text.translatable("screen.serverscanner.start_scan")));
        });
    }

    private boolean isMinecraftServerOnline(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 1000);
            socket.setSoTimeout(1000);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(baos);

            handshake.writeByte(0x00);
            writeVarInt(handshake, 758);
            writeVarInt(handshake, ip.length());
            handshake.writeBytes(ip);
            handshake.writeShort(port);
            writeVarInt(handshake, 1);

            byte[] handshakeBytes = baos.toByteArray();

            writeVarInt(out, handshakeBytes.length);
            out.write(handshakeBytes);

            out.writeByte(0x01);
            out.writeByte(0x00);

            int packetLength = readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId != 0x00) return false;

            int jsonLength = readVarInt(in);
            if (jsonLength < 0) return false;
            byte[] jsonData = new byte[jsonLength];
            in.readFully(jsonData);

            String json = new String(jsonData, StandardCharsets.UTF_8);
            return json.contains("version") && json.contains("players");

        } catch (Exception e) {
            return false;
        }
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            byte temp = (byte)(value & 0b01111111);
            value >>>= 7;
            if (value != 0) {
                temp |= 0b10000000;
            }
            out.writeByte(temp);
        } while (value != 0);
    }

    private void addAllServers() {
        ServerListAccessor accessor = (ServerListAccessor) serverList;

        synchronized (foundServers) {
            List<ServerInfo> currentServers = accessor.getServers();

            for (String addr : foundServers) {
                String[] parts = addr.split(":");
                if (parts.length != 2) continue;
                String ip = parts[0];
                int port;
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                ServerInfo newServer = new ServerInfo("Scanned: " + addr, ip + ":" + port, ServerInfo.ServerType.OTHER);

                boolean exists = currentServers.stream()
                        .anyMatch(s -> s.address.equals(newServer.address) && s.name.equals(newServer.name));

                if (!exists) {
                    currentServers.add(newServer);
                }
            }

            serverList.saveFile();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        int widthCenter = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, widthCenter, 5, 0xFFFFFFFF);

        int statusY = 90 + 25;
        int rectWidth = 220;
        int rectHeight = 35;
        int rectX = widthCenter - rectWidth / 2;
        int rectY = statusY - 5;
        context.fill(rectX, rectY, rectX + rectWidth, rectY + rectHeight, 0x90000000);

        context.drawCenteredTextWithShadow(this.textRenderer,
        		Text.translatable("screen.serverscanner.ports_checked", checkedPortsCount), widthCenter, statusY, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
        		Text.translatable("screen.serverscanner.servers_found", openServersCount), widthCenter, statusY + 15, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }
}
