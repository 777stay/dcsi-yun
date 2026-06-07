import matplotlib.pyplot as plt
import matplotlib.patches as patches

# Set up the figure
fig, ax = plt.subplots(figsize=(10, 6))

# Draw a large rectangle representing the Main Memory (RAM)
ax.add_patch(patches.Rectangle((0, 0), 1, 0.8, edgecolor="black", facecolor="lightgray", lw=2))
ax.text(0.5, 0.9, "Main Memory (RAM)", horizontalalignment="center", verticalalignment="center", fontsize=12)

# Draw smaller rectangles for L3, L2, and L1 caches
ax.add_patch(patches.Rectangle((0.05, 0.55), 0.3, 0.3, edgecolor="black", facecolor="lightblue", lw=2))
ax.text(0.2, 0.75, "L3 Cache", horizontalalignment="center", verticalalignment="center", fontsize=12)

ax.add_patch(patches.Rectangle((0.4, 0.65), 0.3, 0.2, edgecolor="black", facecolor="lightgreen", lw=2))
ax.text(0.55, 0.75, "L2 Cache", horizontalalignment="center", verticalalignment="center", fontsize=12)

ax.add_patch(patches.Rectangle((0.75, 0.7), 0.2, 0.15, edgecolor="black", facecolor="lightcoral", lw=2))
ax.text(0.85, 0.775, "L1 Cache", horizontalalignment="center", verticalalignment="center", fontsize=12)

# Add lines for data flow
ax.plot([0.1, 0.1], [0.55, 0.8], color="black", lw=1, ls="--")  # L3 to L2
ax.plot([0.1, 0.4], [0.55, 0.65], color="black", lw=1, ls="--")  # L3 to L2
ax.plot([0.1, 0.75], [0.55, 0.7], color="black", lw=1, ls="--")  # L3 to L1

# Add arrows representing flow of data between components
ax.annotate("", xy=(0.85, 0.725), xytext=(0.75, 0.725), arrowprops=dict(arrowstyle="->", lw=2))  # L1 to CPU
ax.annotate("", xy=(0.55, 0.75), xytext=(0.85, 0.75), arrowprops=dict(arrowstyle="->", lw=2))  # L2 to L1

# Draw CPU and connect it to L1 cache
ax.add_patch(patches.Rectangle((0.75, 0.85), 0.2, 0.1, edgecolor="black", facecolor="yellow", lw=2))
ax.text(0.85, 0.9, "CPU", horizontalalignment="center", verticalalignment="center", fontsize=12)

# Draw the Label for the cache hierarchy flow
ax.text(0.5, -0.05, "Data Flow (L3 -> L2 -> L1 -> CPU)", horizontalalignment="center", verticalalignment="center", fontsize=14)

# Hide axes for cleaner visual
ax.set_xlim(-0.1, 1.1)
ax.set_ylim(-0.1, 1)
ax.axis('off')

# Show the plot
plt.show()
