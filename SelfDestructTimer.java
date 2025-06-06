import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.List;

public class SelfDestructTimer
{
    private final int countdownSeconds = 10;
    private final int totalMilliseconds = countdownSeconds * 1000;
    private long startTime;
    private float pulseAlpha = 1.0f;
    private boolean fadeOut = true;
    private boolean running = true;
    private float redOverlayAlpha = 0.0f;
    private boolean countdownDone = false;
    private final Timer timer = new Timer();

    private final List<OverlayWindow> windows = new ArrayList<>();

    public SelfDestructTimer()
    {
        startTime = System.currentTimeMillis();

        // Create a transparent overlay for every screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice device : ge.getScreenDevices())
        {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            OverlayWindow window = new OverlayWindow(bounds);
            windows.add(window);
            window.setVisible(true);
        }

        timer.scheduleAtFixedRate(new TimerTask()
        {
            public void run()
            {
                if (!running)
                    return;

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= totalMilliseconds && !countdownDone)
                {
                    countdownDone = true;
                    new RedFadeThread().start();
                }

                for (OverlayWindow w : windows)
                    w.repaint();
            }
        }, 0, 16); // ~60 FPS
    }

    private class RedFadeThread extends Thread
    {
        public void run()
        {
            while (redOverlayAlpha < 1.0f)
            {
                redOverlayAlpha += 0.01f;
                for (OverlayWindow w : windows)
                    w.repaint();
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
            // Keep solid red after fade
            redOverlayAlpha = 1.0f;
        }
    }

    private class OverlayWindow extends JWindow
    {
        private final Rectangle bounds;

        public OverlayWindow(Rectangle bounds)
        {
            this.bounds = bounds;
            setAlwaysOnTop(true);
            setBackground(new Color(0, 0, 0, 0));
            setBounds(bounds);

            TimerPanel panel = new TimerPanel(bounds);
            setContentPane(panel);

            panel.setFocusable(true);
            panel.requestFocusInWindow();

            panel.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyPressed(KeyEvent e)
                {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                    {
                        running = false;
                        timer.cancel();
                        for (OverlayWindow w : windows)
                            w.dispose();
                    }
                }
            });
        }
    }

    private class TimerPanel extends JPanel
    {
        private final Rectangle bounds;

        public TimerPanel(Rectangle bounds)
        {
            this.bounds = bounds;
            setOpaque(false);
        }

        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = bounds.width;
            int h = bounds.height;

            // Background pulse (before red overlay starts)
            if (!countdownDone)
            {
                if (fadeOut) pulseAlpha -= 0.01f; else pulseAlpha += 0.01f;
                if (pulseAlpha <= 0.5f) fadeOut = false;
                if (pulseAlpha >= 1.0f) fadeOut = true;

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha * 0.3f));
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, w, h);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            int remainingMs = Math.max(0, totalMilliseconds - (int)elapsed);
            int remainingSeconds = remainingMs / 1000;
            int millis = remainingMs % 1000;

            // Big countdown
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2.setColor(Color.RED);
            g2.setFont(new Font("Arial", Font.BOLD, 150));
            String timeStr = String.valueOf(remainingSeconds);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(timeStr);
            g2.drawString(timeStr, (w - textWidth) / 2, h / 2);

            // Circle of ticks
            int radius = 150;
            int cx = w / 2;
            int cy = h / 2;
            for (int i = 0; i < 1000; i += 10)
            {
                double angle = 2 * Math.PI * i / 1000.0;
                int x = (int)(cx + Math.cos(angle) * radius);
                int y = (int)(cy + Math.sin(angle) * radius);
                g2.setColor(i <= millis ? Color.RED : new Color(100, 0, 0, 100));
                g2.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
            }

            // Red overlay (including solid after fade)
            if (redOverlayAlpha > 0.0f)
            {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, redOverlayAlpha));
                g2.setColor(Color.RED);
                g2.fillRect(0, 0, w, h);
            }

            g2.dispose();
        }
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(SelfDestructTimer::new);
    }
}
