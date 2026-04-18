package com.screenshot.ui;

import com.screenshot.config.AppConfig;
import com.screenshot.config.ImageFormat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 设置对话框
 * 提供图形化的配置界面
 * 支持空闲/忙碌双时段截图间隔 + 空闲判定阈值配置
 *
 * @author wsj
 * @version 2.0.0
 */
public class SettingsDialog extends JDialog {
    private AppConfig config;
    private boolean saved = false;

    // UI组件
    private JTextField idleIntervalField;
    private JTextField busyIntervalField;
    private JTextField idleThresholdField;
    private JTextField pathField;
    private JComboBox<String> formatCombo;
    private JCheckBox archiveEnabledCheckbox;
    private JTextField archiveTimeField;
    private JButton browseButton;
    private JButton saveButton;
    private JButton cancelButton;

    /**
     * 创建设置对话框
     *
     * @param parent 父窗口
     * @param config 当前配置
     */
    public SettingsDialog(Frame parent, AppConfig config) {
        super(parent, "设置", true);
        this.config = config;

        initializeUI();
        loadConfig();

        setSize(550, 480);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    /**
     * 初始化UI
     */
    private void initializeUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 表单面板
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // ===== 截图间隔区域标题 =====
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel sectionTitle = new JLabel("截图时间间隔设置");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD));
        formPanel.add(sectionTitle, gbc);

        row++;

        // 说明文字
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        JLabel descLabel = new JLabel("<html><body style='width:450px'>"
                + "程序根据键鼠活动自动切换截图频率：<br>"
                + "• 检测到键鼠操作 → 立即切换为忙碌状态，按忙碌间隔截图<br>"
                + "• 无键鼠操作超过阈值 → 切换为空闲状态，按空闲间隔截图"
                + "</body></html>");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN).deriveFont(11f));
        descLabel.setForeground(Color.GRAY);
        formPanel.add(descLabel, gbc);

        row++;

        // 空闲时段间隔
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("空闲时段间隔 (秒):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        idleIntervalField = new JTextField(15);
        formPanel.add(idleIntervalField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("(1-3600)"), gbc);

        row++;

        // 忙碌时段间隔
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("忙碌时段间隔 (秒):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        busyIntervalField = new JTextField(15);
        formPanel.add(busyIntervalField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("(1-3600)"), gbc);

        row++;

        // 空闲判定阈值
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("空闲判定阈值 (秒):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        idleThresholdField = new JTextField(15);
        formPanel.add(idleThresholdField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("(10-3600)"), gbc);

        row++;

        // 分隔线
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JSeparator(), gbc);

        row++;

        // 保存路径
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("保存路径:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        pathField = new JTextField(15);
        formPanel.add(pathField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        browseButton = new JButton("浏览...");
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browsePath();
            }
        });
        formPanel.add(browseButton, gbc);

        row++;

        // 图片格式
        gbc.gridx = 0;
        gbc.gridy = row;
        formPanel.add(new JLabel("图片格式:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formatCombo = new JComboBox<>(new String[]{"PNG", "JPG", "BMP"});
        formPanel.add(formatCombo, gbc);

        row++;

        // ===== 归档设置分隔线 =====
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JSeparator(), gbc);

        row++;

        // 归档设置标题
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel archiveTitle = new JLabel("每日归档设置");
        archiveTitle.setFont(archiveTitle.getFont().deriveFont(Font.BOLD));
        formPanel.add(archiveTitle, gbc);

        row++;

        // 启用归档
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        archiveEnabledCheckbox = new JCheckBox("启用每日自动归档（将前一天截图移入日期子目录）");
        formPanel.add(archiveEnabledCheckbox, gbc);

        row++;

        // 归档时间
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("归档执行时间:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        archiveTimeField = new JTextField(15);
        formPanel.add(archiveTimeField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("(HH:mm)"), gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        saveButton = new JButton("保存");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfig();
            }
        });

        cancelButton = new JButton("取消");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * 加载配置到UI
     */
    private void loadConfig() {
        idleIntervalField.setText(String.valueOf(config.getIdleInterval()));
        busyIntervalField.setText(String.valueOf(config.getBusyInterval()));
        idleThresholdField.setText(String.valueOf(config.getIdleThreshold()));
        pathField.setText(config.getSavePath());
        formatCombo.setSelectedItem(config.getImageFormat().name());
        archiveEnabledCheckbox.setSelected(config.isArchiveEnabled());
        archiveTimeField.setText(config.getArchiveTime());
    }

    /**
     * 浏览保存路径
     */
    private void browsePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择保存路径");
        chooser.setApproveButtonText("选择");
        chooser.setApproveButtonToolTipText("选择当前文件夹作为保存路径");

        String currentPath = pathField.getText().trim();
        if (!currentPath.isEmpty()) {
            java.io.File currentDir = new java.io.File(currentPath);
            if (currentDir.exists()) {
                chooser.setCurrentDirectory(currentDir);
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        try {
            long idleInterval = Long.parseLong(idleIntervalField.getText().trim());
            if (idleInterval < 1 || idleInterval > 3600) {
                JOptionPane.showMessageDialog(this,
                    "空闲时段间隔必须在1-3600秒之间",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            long busyInterval = Long.parseLong(busyIntervalField.getText().trim());
            if (busyInterval < 1 || busyInterval > 3600) {
                JOptionPane.showMessageDialog(this,
                    "忙碌时段间隔必须在1-3600秒之间",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            long idleThreshold = Long.parseLong(idleThresholdField.getText().trim());
            if (idleThreshold < 10 || idleThreshold > 3600) {
                JOptionPane.showMessageDialog(this,
                    "空闲判定阈值必须在10-3600秒之间",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            String path = pathField.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "保存路径不能为空",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 检查路径是否存在，不存在则创建
            Path savePath = Paths.get(path);
            if (!Files.exists(savePath)) {
                int create = JOptionPane.showConfirmDialog(this,
                    "路径不存在，是否创建？",
                    "创建路径",
                    JOptionPane.YES_NO_OPTION);

                if (create == JOptionPane.YES_OPTION) {
                    Files.createDirectories(savePath);
                } else {
                    return;
                }
            }

            // 更新配置
            config.setIdleInterval(idleInterval);
            config.setBusyInterval(busyInterval);
            config.setIdleThreshold(idleThreshold);
            config.setSavePath(path);
            config.setImageFormat(ImageFormat.valueOf((String) formatCombo.getSelectedItem()));
            config.setArchiveEnabled(archiveEnabledCheckbox.isSelected());

            String archiveTime = archiveTimeField.getText().trim();
            if (!archiveTime.matches("^([01]\\d|2[0-3]):([0-5]\\d)$")) {
                JOptionPane.showMessageDialog(this,
                    "归档时间格式错误，应为HH:mm格式，如 00:30",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            config.setArchiveTime(archiveTime);

            saved = true;
            dispose();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "请输入有效的数字",
                "输入错误",
                JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "保存失败: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 检查是否已保存
     *
     * @return 是否已保存
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * 获取配置
     *
     * @return 配置对象
     */
    public AppConfig getConfig() {
        return config;
    }
}
