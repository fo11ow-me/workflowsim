import json
import os
import re
from datetime import datetime
from itertools import combinations

import matplotlib
import matplotlib as mpl
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
import statsmodels.api as sm
from scipy.stats import f_oneway
from statsmodels.formula.api import ols
from statsmodels.stats.multicomp import pairwise_tukeyhsd
import scienceplots

# ----------------------------
# Matplotlib / Style Settings
# ----------------------------
matplotlib.use("TkAgg")
plt.style.use(["science", "ieee", "no-latex"])

mpl.rcParams["axes.formatter.useoffset"] = False
mpl.rcParams["axes.formatter.use_mathtext"] = True
mpl.rcParams["axes.formatter.limits"] = (0, 0)  # Always use scientific notation


# ----------------------------
# Utility Functions
# ----------------------------
def load_data(path):
    """Load data from .jsonl, .json, or .csv into list of dicts."""
    data = []
    if path.endswith(".jsonl"):
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    data.append(json.loads(line))
    elif path.endswith(".json"):
        with open(path, "r", encoding="utf-8") as f:
            raw = json.load(f)
            data = raw if isinstance(raw, list) else [raw]
    elif path.endswith(".csv"):
        df = pd.read_csv(path)
        data = df.to_dict(orient="records")
    else:
        raise ValueError(f"Unsupported file format: {path}")

    print(f"\n✅ Loading {len(data)} records from {path}")
    return data


def save_text(path, content):
    """Save plain text content to file."""
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def save_plot(fig, path):
    """Save matplotlib figure to file and close it."""
    fig.savefig(path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    print(f"✅ Plot save to: {path}")


# ----------------------------
# Plotting Functions
# ----------------------------
def save_pointplot(df, group_var, target_var, plot_path, rpd):
    """Create and save a mean ± 95% CI pointplot using SciencePlots style."""
    fig, ax = plt.subplots(figsize=(8, 5))
    sns.pointplot(
        x=group_var,
        y=target_var,
        data=df,
        capsize=0.05,
        palette=["blue"],
        ax=ax,
        err_kws={"linestyle": "-"},
    )
    ax.set_xlabel(group_var)
    ax.set_ylabel(f"{target_var} (%)" if rpd else target_var)
    plt.tight_layout()
    save_plot(fig, plot_path)


def compare(path, x_axis="deadlineFactor", y_axis="elecCost",
            output_dir=".", rpd=True):
    """Create a comparison pointplot for multiple algorithms."""
    raw_data = load_data(path)
    df = pd.DataFrame(raw_data)

    # Compute RPD if needed
    if rpd:
        best_known = df[y_axis].min()
        rpd_name = f"{y_axis}Rpd"
        df[rpd_name] = ((df[y_axis] - best_known) / best_known) * 100
        y_axis = rpd_name

    # Clean algorithm names
    df["name"] = df["name"].apply(lambda x: re.sub(r"\(.*?\)", "", x).strip())

    # Save path setup
    base_name = os.path.splitext(os.path.basename(path))[0]
    save_path = os.path.join(output_dir, f"{base_name}_{x_axis}_{y_axis}.pdf")

    fig, ax = plt.subplots(figsize=(8, 5))
    hue_count = df["name"].nunique()

    plot_params = dict(
        data=df,
        x=x_axis,
        y=y_axis,
        hue="name",
        capsize=0.05,
        err_kws={"linestyle": "-"},
        markers=["o", "s", "D", "^", "v", "P", "*", "X"],
        linestyles="-",
        palette="Set2",
        ax=ax,
    )

    if hue_count > 1:
        plot_params["dodge"] = 0.2

    sns.pointplot(**plot_params)
    ax.set_xlabel(x_axis)
    ax.set_ylabel(f"{y_axis} (%)" if rpd else y_axis)

    ax.legend(
        loc="upper right",
        frameon=False,
    )

    plt.tight_layout()
    save_plot(fig, save_path)


# ----------------------------
# ANOVA Functions
# ----------------------------
def anova(path, group_vars=None, target_var="elecCost",
          output_dir=".", rpd=True):
    """Run one-way or multi-way ANOVA depending on group_vars."""
    if not group_vars:
        raise ValueError("group_vars must contain at least one variable.")

    if not (path and os.path.exists(path)):
        raise ValueError("File does not exist.")

    raw_data = load_data(path)
    df = pd.DataFrame(raw_data)
    base_name = os.path.splitext(os.path.basename(path))[0]
    os.makedirs(output_dir, exist_ok=True)

    if rpd:
        best_known = df[target_var].min()
        rpd_name = f"{target_var}Rpd"
        df[rpd_name] = ((df[target_var] - best_known) / best_known) * 100
        target_var = rpd_name

    if len(group_vars) == 1:
        _one_way_anova(df, base_name, group_vars[0],
                       target_var, output_dir, rpd)
    else:
        _multi_way_anova(df, base_name, group_vars,
                         target_var, output_dir, rpd)


def _one_way_anova(df, base_name, group_var, target_var,
                   output_dir, rpd):
    """Perform one-way ANOVA and Tukey HSD test."""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    groups = df.groupby(group_var)[target_var].apply(list)

    print("\n--- One-Way ANOVA ---")
    for name, values in groups.items():
        print(f"{name}: n={len(values)}")

    f_stat, p_val = f_oneway(*groups)
    df_total = len(df) - 1
    df_between = len(groups) - 1
    df_within = df_total - df_between
    conclusion = (
        "✅ Significant difference." if p_val < 0.05
        else "❌ No significant difference."
    )

    print(f"\nF-statistic: {f_stat:.4f}")
    print(f"p-value: {p_val:.4f}")
    print(f"Degrees of Freedom: between={df_between}, within={df_within}")
    print("Conclusion:", conclusion)

    # Tukey HSD
    print("\n--- Tukey HSD Post-Hoc Test ---")
    tukey = pairwise_tukeyhsd(
        endog=df[target_var],
        groups=df[group_var],
        alpha=0.05,
    )
    print(tukey.summary())

    tukey_path = os.path.join(output_dir, f"{base_name}_tukey.csv")
    pd.DataFrame(
        tukey._results_table.data[1:],
        columns=tukey._results_table.data[0],
    ).to_csv(tukey_path, index=False)
    print("✅ Tukey HSD result saved to:", tukey_path)

    # Save summary
    summary = (
        f"ANOVA Summary for {base_name}\n"
        f"Timestamp: {timestamp}\n"
        f"Analysis Type: One-Way ANOVA\n"
        f"Target Variable: {target_var}\n"
        f"Grouping Variable: {group_var}\n"
        f"F-statistic: {f_stat:.4f}\n"
        f"p-value: {p_val:.4f}\n"
        f"Degrees of Freedom: between={df_between}, within={df_within}\n"
        f"Conclusion: {conclusion}\n\n"
        f"Tukey HSD Results:\n{tukey.summary()}\n"
    )
    save_text(os.path.join(output_dir, f"{base_name}_anova_summary.txt"), summary)

    # Plot
    save_pointplot(
        df, group_var, target_var,
        os.path.join(output_dir, f"{base_name}_pointplot_{group_var}.pdf"),
        rpd,
    )


def _multi_way_anova(df, base_name, group_vars, target_var,
                     output_dir, rpd):
    """Perform multi-way ANOVA and Tukey HSD for significant main effects."""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print("\n--- Multi-Way ANOVA ---")

    # Ensure categorical grouping variables
    for var in group_vars:
        df[var] = df[var].astype("category")

    # Build formula with interactions
    formula_terms = [
        ":".join(comb)
        for r in range(1, len(group_vars) + 1)
        for comb in combinations(group_vars, r)
    ]
    formula = f"{target_var} ~ " + " + ".join(formula_terms)
    print("Model formula:", formula)

    # Fit model
    model = ols(formula, data=df).fit()
    anova_table = sm.stats.anova_lm(model, typ=2)
    print("\nANOVA Table:")
    print(anova_table)

    # Save ANOVA table
    anova_path = os.path.join(output_dir, f"{base_name}_anova_table.csv")
    anova_table.to_csv(anova_path)
    print("📊 ANOVA table saved to:", anova_path)

    # Tukey tests for significant main effects
    significant_factors = anova_table.index[
        (anova_table["PR(>F)"] < 0.05) & (anova_table.index != "Residual")
        ]
    tukey_summaries = ""

    for factor in significant_factors:
        if ":" not in factor:  # Skip interactions
            try:
                print(f"\nPerforming Tukey HSD test for factor: {factor}")
                tukey = pairwise_tukeyhsd(
                    endog=df[target_var],
                    groups=df[factor],
                    alpha=0.05,
                )
                print(tukey.summary())

                tukey_summaries += (
                    f"\nTukey HSD Results for {factor}:\n{tukey.summary()}\n"
                )
                tukey_path = os.path.join(
                    output_dir,
                    f"{base_name}_tukey_{factor.replace(':', '_')}.csv",
                )
                pd.DataFrame(
                    tukey._results_table.data[1:],
                    columns=tukey._results_table.data[0],
                ).to_csv(tukey_path, index=False)
                print(f"✅ Tukey HSD results saved to: {tukey_path}")
            except Exception as e:
                print(f"❌ Tukey HSD test for {factor} failed: {e}")
        else:
            print(f"Skipping Tukey test for interaction term '{factor}' (not supported)")

    # Save summary
    summary = (
        f"ANOVA Summary for {base_name}\n"
        f"Timestamp: {timestamp}\n"
        f"Analysis Type: Multi-Way ANOVA\n"
        f"Target Variable: {target_var}\n"
        f"Grouping Variables: {', '.join(group_vars)}\n\n"
        f"{anova_table}\n"
        f"{tukey_summaries}"
    )
    save_text(os.path.join(output_dir, f"{base_name}_anova_summary.txt"), summary)

    # Plots for each grouping variable
    for var in group_vars:
        save_pointplot(
            df, var, target_var,
            os.path.join(output_dir, f"{base_name}_pointplot_{var}.pdf"),
            rpd,
        )
